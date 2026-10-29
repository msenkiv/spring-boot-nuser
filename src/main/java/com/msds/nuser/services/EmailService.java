package com.msds.nuser.services;

import org.springframework.stereotype.Service;

@Service
public class EmailService extends BaseEmailService {

    public void sendConfirmationEmail(String destinationEmail){
        System.out.println("Preparing confirmation email.");
        super.sendEmail(destinationEmail);

    }

    public void sendUpdatedInformationEmail(String destinationEmail){
        System.out.println("Preparing updated information email.");
        super.sendEmail(destinationEmail);
    }


}
