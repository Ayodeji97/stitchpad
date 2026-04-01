package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.domain.model.User

fun UserDto.toUser(): User = User(
    id = id,
    email = email,
    displayName = displayName,
    businessName = businessName,
    phoneNumber = phoneNumber,
    avatarColorIndex = avatarColorIndex
)

fun User.toUserDto(): UserDto = UserDto(
    id = id,
    email = email,
    displayName = displayName,
    businessName = businessName,
    phoneNumber = phoneNumber,
    avatarColorIndex = avatarColorIndex
)
