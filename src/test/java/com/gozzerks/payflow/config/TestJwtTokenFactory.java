package com.gozzerks.payflow.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class TestJwtTokenFactory {

    public static String generateToken(String... scopes) {
        try {
            byte[] secret = TestSecurityConfig.TEST_SECRET.getBytes(StandardCharsets.UTF_8);
            MACSigner signer = new MACSigner(secret);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("test-user")
                    .issuer("test")
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("scope", String.join(" ", scopes))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test JWT", e);
        }
    }
}