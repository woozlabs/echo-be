package woozlabs.echo.domain.member.service;

import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import woozlabs.echo.domain.member.dto.AddAccountRequestDto;
import woozlabs.echo.domain.member.dto.SignInRequestDto;
import woozlabs.echo.domain.member.entity.Member;
import woozlabs.echo.domain.member.entity.Role;
import woozlabs.echo.domain.member.entity.SubAccount;
import woozlabs.echo.domain.member.entity.SuperAccount;
import woozlabs.echo.domain.member.repository.MemberRepository;
import woozlabs.echo.domain.member.repository.SubAccountRepository;
import woozlabs.echo.domain.member.repository.SuperAccountRepository;
import woozlabs.echo.global.exception.CustomErrorException;
import woozlabs.echo.global.exception.ErrorCode;
import woozlabs.echo.global.token.service.AccessTokenService;
import woozlabs.echo.global.utils.FirebaseTokenVerifier;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final SuperAccountRepository superAccountRepository;
    private final SubAccountRepository subAccountRepository;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final AccessTokenService accessTokenService;

    @Transactional
    public void signIn(SignInRequestDto requestDto) {
        Member member = Member.builder()
                .uid(requestDto.getUid())
                .displayName(requestDto.getDisplayName())
                .email(requestDto.getEmail())
                .emailVerified(requestDto.isEmailVerified())
                .photoURL(requestDto.getPhotoURL())
                .role(Role.ROLE_USER)
                .build();

        memberRepository.save(member);

        SuperAccount superAccount = SuperAccount.builder()
                .uid(requestDto.getUid())
                .displayName(requestDto.getDisplayName())
                .email(requestDto.getEmail())
                .emailVerified(requestDto.isEmailVerified())
                .photoURL(requestDto.getPhotoURL())
                .role(Role.ROLE_USER)
                .member(member)
                .build();

        superAccountRepository.save(superAccount);

        accessTokenService.saveAccessToken(member.getId(), requestDto.getGoogleAccessToken());
    }

    @Transactional
    public void addAccount(String idToken, AddAccountRequestDto requestDto) throws FirebaseAuthException {
        String superAccountUid = firebaseTokenVerifier.verifyTokenAndGetUid(idToken);
        SuperAccount superAccount = superAccountRepository.findByUid(superAccountUid)
                .orElseThrow(() -> new CustomErrorException(ErrorCode.NOT_FOUND_SUPER_ACCOUNT));

        Member member = Member.builder()
                .uid(requestDto.getUid())
                .displayName(requestDto.getDisplayName())
                .email(requestDto.getEmail())
                .emailVerified(requestDto.isEmailVerified())
                .photoURL(requestDto.getPhotoURL())
                .role(Role.ROLE_USER)
                .build();

        memberRepository.save(member);

        SubAccount subAccount = SubAccount.builder()
                .uid(requestDto.getUid())
                .displayName(requestDto.getDisplayName())
                .email(requestDto.getEmail())
                .emailVerified(requestDto.isEmailVerified())
                .photoURL(requestDto.getPhotoURL())
                .role(Role.ROLE_USER)
                .member(member)
                .superAccount(superAccount)
                .build();

        subAccountRepository.save(subAccount);

        accessTokenService.saveAccessToken(member.getId(), requestDto.getGoogleAccessToken());
    }
}
