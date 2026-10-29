package com.msds.nuser.dtos;

import java.util.List;

public record PageResponseDto<TData>(
        List<TData> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}