package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Schema(description = "用户组信息")
public class UserGroupDto {
    @Schema(description = "用户组 ID")
    private Long id;
    
    @Schema(description = "用户组名称")
    private String name;
    
    @Schema(description = "用户组描述")
    private String description;
    
    @Schema(description = "成员数量")
    private int memberCount;
    
    @Schema(description = "关联模板数量")
    private int templateCount;
    
    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}