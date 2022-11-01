package com.feelmycode.parabole.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class OrderWithCouponResponseDto {

        private Long sellerId;
        private String storeName;
        private List<OrderInfoResponseDto> getItemList;
        private CouponResponseDto getCouponList;

        public OrderWithCouponResponseDto(Long sellerId, String storeName,
            List<OrderInfoResponseDto> getItemList, CouponResponseDto getCouponList) {
            this.sellerId = sellerId;
            this.storeName = storeName;
            this.getItemList = getItemList;
            this.getCouponList = getCouponList;
        }
    }
