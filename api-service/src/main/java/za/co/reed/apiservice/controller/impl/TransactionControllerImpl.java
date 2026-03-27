package za.co.reed.apiservice.controller.impl;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.apiservice.controller.TransactionController;
import za.co.reed.apiservice.dto.response.DataResponse;
import za.co.reed.apiservice.dto.response.TransactionResponse;
import za.co.reed.apiservice.service.TransactionService;

@RequiredArgsConstructor
@RestController
public class TransactionControllerImpl implements TransactionController {
    private final TransactionService transactionService;

    @Override
    public ResponseEntity<DataResponse<TransactionResponse>> list(String accountId, TransactionStatus status, Date from, Date to,
                                                                  int page, int pageSize, String order, String sort) {
        return transactionService.list(accountId, status, from, to, page, pageSize, order, sort);
    }

    @Override
    public ResponseEntity<TransactionResponse> getById(UUID id) {
        return transactionService.getByExternalId(id);
    }

    @Override
    public ResponseEntity<DataResponse<TransactionResponse>> merchantSearch(String merchantName, Date from, Date to,
                                                                            int page, int pageSize, String order,
                                                                            String sort) {
        return transactionService.merchantSearch(merchantName, from, to, page, pageSize, order, sort);
    }
}
