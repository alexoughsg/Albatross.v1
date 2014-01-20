package org.apache.cloudstack.mom.simulator;

import java.util.Date;

public class LocalGenerator {

    protected String generateRandString()
    {
        int length = 10;
        String alpha = "abcdefghijklmnopqrstuvwxyz";
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < length; i++) {
            double index = Math.random() * alpha.length();
            buffer.append(alpha.charAt((int) index));
        }
        return buffer.toString();
    }

    protected Date generateRandDate()
    {
        Date date = new Date();
        long time = date.getTime();
        time -= 60 * 60 * 1000 * 24;
        date.setTime(time);
        return date;
    }
}
