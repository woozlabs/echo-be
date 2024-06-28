package woozlabs.echo.domain.gmail.service;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.model.Thread;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import woozlabs.echo.domain.gmail.dto.GmailThreadListAttachments;
import woozlabs.echo.domain.gmail.dto.GmailThreadListThreads;
import woozlabs.echo.domain.gmail.exception.GmailException;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static woozlabs.echo.global.constant.GlobalConstant.*;

@Service
public class AsyncGmailService {
    private final String THREAD_PAYLOAD_HEADER_SUBJECT_KEY = "Subject";
    private final String THREAD_PAYLOAD_HEADER_FROM_KEY = "From";
    private final String THREAD_PAYLOAD_HEADER_DATE_KEY = "Date";
    private final String SPLIT_SENDER_DATA_ERR_MSG = "발신자의 데이터를 분리할 수 없습니다.";

    @Async
    public CompletableFuture<GmailThreadListThreads> asyncRequestGmailThreadGetForList(Thread thread, Gmail gmailService){
        try {
            String id = thread.getId();
            String snippet = thread.getSnippet();
            BigInteger historyId = thread.getHistoryId();
            GmailThreadListThreads gmailThreadListThreads= new GmailThreadListThreads();
            Thread detailedThread = gmailService.users().threads().get(USER_ID, id)
                    .setFormat(THREADS_GET_FULL_FORMAT)
                    .execute();
            List<Message> messages = detailedThread.getMessages();
            Message topMessage = messages.stream().findFirst().orElseThrow(() -> new EntityNotFoundException("test"));
            MessagePart payload = topMessage.getPayload();
            List<MessagePartHeader> headers = payload.getHeaders(); // parsing header
            headers.forEach((header) -> {
                switch (header.getName()) {
                    case THREAD_PAYLOAD_HEADER_SUBJECT_KEY -> gmailThreadListThreads.setSubject(header.getValue());
                    case THREAD_PAYLOAD_HEADER_FROM_KEY -> {
                        String sender = header.getValue();
                        List<String> splitSender = splitSenderData(sender);
                        if(splitSender.size() != 1) gmailThreadListThreads.setFromEmail(splitSender.get(1));
                        gmailThreadListThreads.setFromName(splitSender.get(0));
                    }
                    case THREAD_PAYLOAD_HEADER_DATE_KEY -> gmailThreadListThreads.setDate(header.getValue());
                }
            });
            List<String> labelIds = topMessage.getLabelIds();
            String mimType = payload.getMimeType();
            List<GmailThreadListAttachments> attachments = new ArrayList<>();
            getAttachments(payload, attachments);
            gmailThreadListThreads.setId(id);
            gmailThreadListThreads.setSnippet(snippet);
            gmailThreadListThreads.setHistoryId(historyId);
            gmailThreadListThreads.setMimeType(mimType);
            gmailThreadListThreads.setLabelIds(labelIds);
            gmailThreadListThreads.setThreadSize(messages.size());
            gmailThreadListThreads.setAttachments(attachments);
            gmailThreadListThreads.setAttachmentSize(attachments.size());
            return CompletableFuture.completedFuture(gmailThreadListThreads);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void getAttachments(MessagePart part, List<GmailThreadListAttachments> attachments){
        if(part.getParts() == null){ // base condition
            if(part.getFilename() != null && !part.getFilename().isBlank()){
                MessagePartBody body = part.getBody();
                attachments.add(GmailThreadListAttachments.builder()
                        .mimeType(part.getMimeType())
                        .fileName(part.getFilename())
                        .attachmentId(body.getAttachmentId())
                        .size(body.getSize()).build()
                );
            }
        }else{ // recursion
            for(MessagePart subPart : part.getParts()){
                getAttachments(subPart, attachments);
            }
            if(part.getFilename() != null && !part.getFilename().isBlank()){
                MessagePartBody body = part.getBody();
                attachments.add(GmailThreadListAttachments.builder()
                        .mimeType(part.getMimeType())
                        .fileName(part.getFilename())
                        .attachmentId(body.getAttachmentId())
                        .size(body.getSize()).build()
                );
            }
        }
    }

    private List<String> splitSenderData(String sender){
        List<String> splitSender = new ArrayList<>();
        String replaceSender = sender.replace("\"", "");
        String regex = "(.*)\\s*<(.*)>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(replaceSender);
        if(matcher.find()){
            splitSender.add(matcher.group(1).trim());
            splitSender.add(matcher.group(2).trim());
        }else{
            splitSender.add(sender);
        }
        return splitSender;
    }
}