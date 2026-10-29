package com.msds.nuser.dtos;

public record ApiResponseDto<TData>(
        int status,
        TData data
){}