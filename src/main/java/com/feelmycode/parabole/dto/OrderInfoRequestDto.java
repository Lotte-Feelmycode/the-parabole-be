package com.feelmycode.parabole.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OrderInfoRequestDto {

    private Long userId;
    private Long orderInfoId;
    private String orderState;

    public OrderInfoRequestDto(Long userId, Long orderInfoId, String orderState) {
        this.userId = userId;
        this.orderInfoId = orderInfoId;
        this.orderState = orderState;
    }

}
