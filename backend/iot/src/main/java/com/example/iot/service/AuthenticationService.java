package com.example.iot.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.iot.dto.request.AuthenticationRequest;
import com.example.iot.dto.request.IntrospectRequest;
import com.example.iot.dto.request.RefreshTokenRequest;
import com.example.iot.dto.response.AuthenticationResponse;
import com.example.iot.dto.response.IntrospectResponse;
import com.example.iot.entity.UserEntity;
import com.example.iot.repository.AuthenticationRepository;
import com.example.iot.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    protected static final String SIGNER_KEY="V7mQ2xLp9Ka4NzT8cRw5YfH1uGj6Ds3EeB0iXqMkP4vLn2ZaC9sWtF7hJ5rUyK8d";
    private final AuthenticationRepository authenticationRepository;
    private final UserRepository userRepository;
    public AuthenticationResponse login(AuthenticationRequest request){
        UserEntity userEntity  = userRepository.findByEmailAndDeletedFalse(request.getEmail()).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        if(userEntity == null){
            throw new RuntimeException("Invalid email or password");
        }
        BCryptPasswordEncoder  encoder = new BCryptPasswordEncoder(10);
        boolean authentication = encoder.matches(request.getPassword(), userEntity.getPassword());
        if(!authentication){
            throw new RuntimeException("Invalid email or password");
        }
        String token = generateToken(userEntity,60*60*1000);
        String refreshToken = generateToken(userEntity,7L*24*60*60*1000);
        AuthenticationResponse  authenticationResponse = AuthenticationResponse.builder()
                                                        .authentication(true)
                                                        .token(token)
                                                        .refreshToken(refreshToken)            
                                                        .build();
        return authenticationResponse;
    }
    public String generateToken(UserEntity userEntity,long time){
        try {
            Set<String> authorities = new HashSet<>();
            userEntity.getRole().forEach(role -> {
                authorities.add("ROLE_" + role.getName());
                role.getPermission().forEach(permission ->
                    authorities.add(permission.getName())
                );
            });
            JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS256);
            JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                                        .subject(userEntity.getEmail())
                                        .issueTime(new Date())
                                        .expirationTime(new Date(System.currentTimeMillis()+time))
                                        .claim("scope", authorities)
                                        .build();
            Payload payload = new Payload(jwtClaimsSet.toJSONObject());
            JWSObject jwsObject = new JWSObject(jwsHeader, payload);
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Token generation failed: " + e.getMessage(),e);
        }
    }
    public AuthenticationResponse refreshToken(RefreshTokenRequest request){
        try {
            SignedJWT signedJWT = SignedJWT.parse(request.getRefreshToken());
            boolean verified = signedJWT.verify(new MACVerifier(SIGNER_KEY.getBytes()));
            Date expireDate = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (!verified || expireDate.before(new Date())) {
                throw new RuntimeException("Refresh token is invalid or expired");
            }
            String email = signedJWT.getJWTClaimsSet().getSubject();
            UserEntity userEntity = userRepository.findByEmailAndDeletedFalse(email).orElseThrow(()->new RuntimeException("Không tồn tại"));
            String token = generateToken(userEntity, 60*60*1000);
            AuthenticationResponse  authenticationResponse = AuthenticationResponse.builder()
                                                        .authentication(true)
                                                        .token(token)
                                                        .refreshToken(request.getRefreshToken())            
                                                        .build();
        return authenticationResponse;
        } catch (Exception e) {
            throw new RuntimeException("Refresh token failed: " + e.getMessage(), e);
        }
    }
    public IntrospectResponse introspect(IntrospectRequest request){
        try {
            String token = request.getToken();
            SignedJWT signedJWT = SignedJWT.parse(token);
            boolean verified = signedJWT.verify(new MACVerifier(SIGNER_KEY.getBytes()));
            Date expireDate = signedJWT.getJWTClaimsSet().getExpirationTime();
            boolean isValid = verified && expireDate.after(new Date());
            IntrospectResponse introspectResponse = new IntrospectResponse();
            introspectResponse.setIsvalid(isValid);
            return introspectResponse;
        } catch (Exception e) {
            throw new RuntimeException(
                "Token generation failed: " + e.getMessage(),
                e
            );
        }
    }
}
