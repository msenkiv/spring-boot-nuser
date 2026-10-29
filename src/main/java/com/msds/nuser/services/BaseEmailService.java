package com.msds.nuser.services;

public abstract class BaseEmailService {

    public void sendEmail(String email){
        System.out.println("######## ---> Sending email to " + email);
        System.out.println("######## ---> Email has been sent! " );
    }
}
