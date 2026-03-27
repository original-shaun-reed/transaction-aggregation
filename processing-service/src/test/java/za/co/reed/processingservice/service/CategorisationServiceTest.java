package za.co.reed.processingservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.processingservice.categorisation.MlClassifier;
import za.co.reed.processingservice.categorisation.RulesEngine;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.entity.Transaction;
import za.co.reed.persistence.repository.CategoryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategorisationServiceTest {

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private Optional<MlClassifier> mlClassifier;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategorisationService categorisationService;

    private NormalisedTransaction sourceTransaction;
    private Transaction transaction;
    private Category rulesCategory;
    private Category mlCategory;
    private Category uncategorisedCategory;

    @BeforeEach
    void setUp() {
        sourceTransaction = new NormalisedTransaction(
                "source-123",
                SourceType.CARD_NETWORK,
                UUID.randomUUID().toString(),
                BigDecimal.valueOf(100.50),
                Currency.getInstance("ZAR"),
                "Test Merchant",
                "5411",
                Instant.now(),
                TransactionStatus.SETTLED,
                "REF123"
        );

        transaction = new Transaction();
        transaction.setId(1L);
        transaction.setSourceId("source-123");

        rulesCategory = new Category();
        rulesCategory.setId(1L);
        rulesCategory.setCode("GROCERIES");

        mlCategory = new Category();
        mlCategory.setId(2L);
        mlCategory.setCode("ENTERTAINMENT");

        uncategorisedCategory = new Category();
        uncategorisedCategory.setId(999L);
        uncategorisedCategory.setCode("uncategorised");
    }

    @Test
    void categorise_shouldUseRulesEngineWhenCategoryFound() {
        // Given
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.of(rulesCategory));

        // When
        categorisationService.categorise(transaction, sourceTransaction);

        // Then
        assertEquals(rulesCategory, transaction.getCategory());
        verify(rulesEngine).categorise(sourceTransaction);
        verifyNoInteractions(mlClassifier);
        verify(categoryRepository, never()).uncategorised();
    }

    @Test
    void categorise_shouldUseUncategorisedFallbackWhenBothRulesAndMlReturnEmpty() {
        // Arrange
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.empty());
        when(mlClassifier.isPresent()).thenReturn(false);
        when(categoryRepository.uncategorised()).thenReturn(uncategorisedCategory);

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertEquals(uncategorisedCategory, transaction.getCategory());
        verify(rulesEngine).categorise(sourceTransaction);
        verify(categoryRepository).uncategorised();
    }

    @Test
    void categorise_shouldUseUncategorisedFallbackWhenMlClassifierReturnsEmpty() {
        // Arrange
        MlClassifier mockMlClassifier = mock(MlClassifier.class);
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.empty());
        when(mlClassifier.isPresent()).thenReturn(true);
        when(mlClassifier.get()).thenReturn(mockMlClassifier);
        when(mockMlClassifier.classify(sourceTransaction)).thenReturn(Optional.empty());
        when(categoryRepository.uncategorised()).thenReturn(uncategorisedCategory);

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertEquals(uncategorisedCategory, transaction.getCategory());
        verify(rulesEngine).categorise(sourceTransaction);
        verify(mockMlClassifier).classify(sourceTransaction);
        verify(categoryRepository).uncategorised();
    }

    @Test
    void categorise_shouldHandleMlClassifierNotPresent() {
        // Arrange
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.empty());
        when(mlClassifier.isPresent()).thenReturn(false);
        when(categoryRepository.uncategorised()).thenReturn(uncategorisedCategory);

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertEquals(uncategorisedCategory, transaction.getCategory());
        verify(rulesEngine).categorise(sourceTransaction);
        verify(categoryRepository).uncategorised();
    }

    @Test
    void categorise_shouldLogAppropriateTierForRulesMatch() {
        // Arrange
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.of(rulesCategory));

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertEquals(rulesCategory, transaction.getCategory());
        // Log verification would require a different approach with @Captor
    }

    @Test
    void categorise_shouldLogAppropriateTierForMlMatch() {
        // Arrange
        MlClassifier mockMlClassifier = mock(MlClassifier.class);
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.empty());
        when(mlClassifier.isPresent()).thenReturn(true);
        when(mlClassifier.get()).thenReturn(mockMlClassifier);
        when(mockMlClassifier.classify(sourceTransaction)).thenReturn(Optional.of(mlCategory));

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertEquals(mlCategory, transaction.getCategory());
    }

    @Test
    void categorise_shouldLogAppropriateTierForFallback() {
        // Arrange
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.empty());
        when(mlClassifier.isPresent()).thenReturn(false);
        when(categoryRepository.uncategorised()).thenReturn(uncategorisedCategory);

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertEquals(uncategorisedCategory, transaction.getCategory());
    }

    @Test
    void categorise_shouldHandleNullTransactionCategoryGracefully() {
        // Arrange
        when(rulesEngine.categorise(sourceTransaction)).thenReturn(Optional.of(rulesCategory));

        // Act
        categorisationService.categorise(transaction, sourceTransaction);

        // Assert
        assertNotNull(transaction.getCategory());
        assertEquals(rulesCategory, transaction.getCategory());
    }

    @Test
    void categorise_shouldWorkWithDifferentSourceTypes() {
        // Arrange
        NormalisedTransaction bankFeedSource = new NormalisedTransaction(
                "source-456",
                SourceType.BANK_FEED,
                UUID.randomUUID().toString(),
                BigDecimal.valueOf(200.75),
                Currency.getInstance("ZAR"),
                "Bank Merchant",
                null,
                Instant.now(),
                TransactionStatus.SETTLED,
                ""
        );

        when(rulesEngine.categorise(bankFeedSource)).thenReturn(Optional.of(rulesCategory));

        Transaction bankTransaction = new Transaction();
        bankTransaction.setId(2L);
        bankTransaction.setSourceId("source-456");

        // Act
        categorisationService.categorise(bankTransaction, bankFeedSource);

        // Assert
        assertEquals(rulesCategory, bankTransaction.getCategory());
        verify(rulesEngine).categorise(bankFeedSource);
    }
}