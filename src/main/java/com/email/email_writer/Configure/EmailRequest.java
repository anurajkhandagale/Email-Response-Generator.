package com.email.email_writer.Configure;

import lombok.Data;

@Data
public class EmailRequest {
    private String emailContent;
    private String tone;
}
