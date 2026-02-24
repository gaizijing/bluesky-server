package com.bluesky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluesky.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 *
 * @author BlueSky Team
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
