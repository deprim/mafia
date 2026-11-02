package com.mafiagame.utils;

import java.security.SecureRandom;

public class JoinCodeGenerator {


    private static final int CODE_LENGTH = 8;
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodeGenerator() {}

    public static String generateJoinCode() {
        StringBuilder codeBuilder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            codeBuilder.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return codeBuilder.toString();
    }



}
