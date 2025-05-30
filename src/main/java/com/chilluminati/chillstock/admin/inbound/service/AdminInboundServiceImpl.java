package com.chilluminati.chillstock.admin.inbound.service;

import com.chilluminati.chillstock.admin.inbound.dto.AdminInboundRequestDTO;
import com.chilluminati.chillstock.admin.inbound.repository.AdminInboundRepository;
import com.chilluminati.chillstock.admin.inbound.vo.AdminInboundRequestVO;
import com.chilluminati.chillstock.admin.inbound.vo.AdminProductVO;
import com.chilluminati.chillstock.admin.inbound.vo.StockVO;
import com.chilluminati.chillstock.admin.warehouse.dto.AdminAreaWithRemainDistanceDto;
import com.chilluminati.chillstock.admin.warehouse.service.AdminWarehouseService;
import com.chilluminati.chillstock.security.EmailUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminInboundServiceImpl implements AdminInboundService{

    private final AdminInboundRepository adminInboundRepository;
    private final AdminWarehouseService  adminWarehouseService;
    /**
     * 입고 요청 목록 조회
     * @param status 상태 필터 (대기/승인/반려)
     * @param size 한 페이지당 조회 수
     * @return 상태에 따른 입고 요청 DTO 리스트
     */
    @Override
    public List<AdminInboundRequestDTO> getInboundList(String status, int offset, int size) {

        List<AdminInboundRequestVO> voList = adminInboundRepository.selectInboundList(status, size , offset);

        return voList.stream().map(vo -> AdminInboundRequestDTO.builder()
                .inboundId(vo.getInboundId())
                .inboundRequestDate(vo.getInboundRequestDate())
                .inboundAmount(vo.getInboundAmount())
                .inboundStatus(vo.getInboundStatus())
                .productName(vo.getProductName())
                .businessId(vo.getBusinessId())
                .rejectReasonMessage(vo.getRejectReasonMessage()).build())
                .collect(Collectors.toList());


    }



    /**
     * 상태별 입고 요청 총 개수 조회 (페이징용)
     * @param status 상태 필터 (nullable)
     * @return 요청 수
     */
    @Override
    public int countInbound(String status) {
        return adminInboundRepository.countInbound(status);
    }



    @Override
    @Transactional
    public Map<String, Integer> approveInboundRequests(List<Integer> inboundIds) {

        int approvedCount = 0;
        int rejectedCount = 0;
        // 1. 로그인한 사용자 userId 가져오기
        Integer userId = getInteger();

        // 2. userId로 adminId 조회
        Integer adminId = adminInboundRepository.findAdminIdByUserId(userId);
        if (adminId == null) {
            throw new RuntimeException("관리자 정보를 찾을 수 없습니다. userId = " + userId);
        }




        for (Integer inboundId : inboundIds) {

            // 3. 추천 가능한 창고 구역 리스트 조회
            List<AdminAreaWithRemainDistanceDto> areaList = adminWarehouseService.getAllAdminAreaWithRemainDistance(inboundId);
            // 4. 입고 요청 상세 조회
            AdminInboundRequestVO inbound = adminInboundRepository.findInboundById(inboundId);
            if (inbound == null) {
                throw new RuntimeException("입고 요청이 존재하지 않습니다. inboundId = " + inboundId);
            }

            //  상태 체크
            if (!"대기".equals(inbound.getInboundStatus())) {
                throw new IllegalStateException("이미 처리된 입고 요청입니다.");
            }

            // 5. 제품 상세 조회
            AdminProductVO product = adminInboundRepository.findProductById(inbound.getProductId());
            if (product == null) {
                throw new RuntimeException("제품 정보가 존재하지 않습니다. productId = " + inbound.getProductId());
            }
            Integer storageIdByTemperature = adminWarehouseService.findStorageIdByTemperature(product.getStorageTemperature());


            // 6. 필요한 전체 공간 계산
            int totalSize = product.getProductSize() * inbound.getInboundAmount();

            // 7. 추천 구역 탐색 (조건: 공간 충분 + 온도 조건 만족 + 거리 가까운 순)
            Optional<AdminAreaWithRemainDistanceDto> matchedAreaOpt = areaList.stream()
                    .filter(area -> area.getRemainSpace() != null && area.getRemainSpace() >= totalSize)
                    .filter(area -> area.getStorageId() == null || area.getStorageId().equals(storageIdByTemperature))
                    .min(Comparator.comparingInt(AdminAreaWithRemainDistanceDto::getDistance));

            // 8.추천 구역이 잇다면 -> 재고로 반영한다.
            if (matchedAreaOpt.isPresent()){
                approvedCount++;
                AdminAreaWithRemainDistanceDto matchedArea = matchedAreaOpt.get();

                StockVO stock = adminInboundRepository.findStockByProductAndArea(product.getProductId(), matchedArea.getAreaId());


                if (stock != null) {
                    // 기존 재고 있으면 수량 추가
                    adminInboundRepository.updateStock(stock.getStockId(), inbound.getInboundAmount());
                } else {
                    // 기존 재고 없으면 신규 등록
                    adminInboundRepository.insertStock(product.getProductId(), matchedArea.getAreaId(), inbound.getInboundAmount());
                }
                // 9. 입고 요청 승인 처리
                adminInboundRepository.approveInboundList(List.of(inboundId), adminId);
            }else {
                // 10. 추천 구역이 없음 -> 자동으로 반려 처리 하기
                rejectedCount++;
                String rejectReasonMessage = getRejectMessage("REJ01"); // 공간 부족
                adminInboundRepository.rejectInbound(inboundId, rejectReasonMessage);
            }


        }
        Map<String, Integer> result = new HashMap<>();
        result.put("approved", approvedCount);
        result.put("rejected", rejectedCount);
        return result;

    }

    @Override
    @Transactional
    public void rejectInboundRequests(List<Integer> inboundIds, String rejectCode) {
        String rejectMessage = getRejectMessage(rejectCode);
        for (Integer inboundId : inboundIds) {

            // 1. 입고요청 읽기 (FOR UPDATE로 락 걸림)
            AdminInboundRequestVO inbound = adminInboundRepository.findInboundById(inboundId);

            // 2. 상태 체크
            if (!"대기".equals(inbound.getInboundStatus())) {
                throw new IllegalStateException("이미 처리된 입고 요청입니다. inboundId=" + inboundId);
            }

            // 3. 반려 처리
            adminInboundRepository.rejectInbound(inboundId, rejectMessage);
        }
    }

    private String getRejectMessage(String code) {
        Map<String, String> rejectReasonMap = Map.of(
                "REJ01", "공간 부족",
                "REJ02", "보관 온도 불일치",
                "REJ03", "시스템 내부 오류",
                "REJ04", "제품 정보 누락",
                "REJ05", "요청 데이터 이상"
        );
        return rejectReasonMap.getOrDefault(code, "기타 사유");
    }

    private static Integer getInteger() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        EmailUserDetails userDetails = (EmailUserDetails) authentication.getPrincipal();
        Integer userId = userDetails.getUserId();
        return userId;
    }
}
