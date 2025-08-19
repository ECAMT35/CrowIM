package com.ecamt35.userservice.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.ecamt35.userservice.constant.CaptchaCacheConstant;
import com.ecamt35.userservice.constant.TemplatePathConstant;
import com.ecamt35.userservice.service.EmailService;
import com.ecamt35.userservice.util.BusinessException;
import com.ecamt35.userservice.util.CaptchaType;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Resource
    private FreeMarkerConfigurer freeMarkerConfigurer;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean sendCaptchaMail(CaptchaType captchaType, String toEmail) {

        String mailKey = CaptchaCacheConstant.MAIL_PREFIX + toEmail;
        String mailValue = stringRedisTemplate.opsForValue().get(mailKey);
        if (StringUtil.isNotBlank(mailValue)) {
            // 1分钟内发送过验证码，拒绝发送到 mail
            return false;
        }

        String key = captchaType.getRedisKey() + toEmail;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (StringUtil.isBlank(code)) {
            code = RandomUtil.randomNumbers(6);
            stringRedisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
        } else {
            // 5分钟内再次获取会延时
            stringRedisTemplate.expire(key, 5, TimeUnit.MINUTES);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("title", captchaType.getSubject());
        model.put("operation", captchaType.getSubject());
        model.put("email", toEmail);
        model.put("captcha", code);

        try {
            Template template = freeMarkerConfigurer.getConfiguration().getTemplate(TemplatePathConstant.CAPTCHA);
            String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            // new
            //注意这里使用的是MimeMessage
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(captchaType.getSubject());
            //第二个参数：格式是否为html
            helper.setText(content, true);

            mailSender.send(message);

        } catch (IOException | TemplateException | MessagingException e) {
            throw new BusinessException(e.getMessage());
        }

        // 设置该邮箱验证码已发送
        stringRedisTemplate.opsForValue().set(mailKey, "1", 1, TimeUnit.MINUTES);
        log.info("MailService.sendMail: {}, to: {}", captchaType.getSubject(), toEmail);
        return true;
    }
}
