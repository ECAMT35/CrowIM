package com.ecamt35.userservice.service;

import com.ecamt35.userservice.util.CaptchaType;
import jakarta.mail.MessagingException;

public interface EmailService {

    boolean sendCaptchaMail(CaptchaType captchaType, String toEmail) throws MessagingException;
}
