package com.amincorporate.seu.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class MemberJoinDTO {

    private String id;

    private String name;

    private Date discordJoinDate;

}
