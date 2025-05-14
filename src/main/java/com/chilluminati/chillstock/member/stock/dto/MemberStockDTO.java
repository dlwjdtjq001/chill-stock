package com.chilluminati.chillstock.member.stock.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MemberStockDTO {
    private Integer stock_id;
    private Integer product_id; //사용자 출고 요청시 사용(출고 요청생성 할때 사용자가 수량 입력, 선택 재고에 맞는 product_id 자동 입력)
    private String product_name;
    private Date expiration_date;
    private Integer stock_amount;
    private LocalDateTime inbound_date;
}
