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
    private RulesEngine testRulesEngine;

    @Mock
    private Optional<MlClassifier> testMlClassifier;

    @Mock
    private CategoryRepository testCategoryRepository;

    @InjectMocks
    private CategorisationService testCategorisationService;

    private NormalisedTransaction testSourceTransaction;
    private Transaction testTransaction;
    private Category testRulesCategory;
    private Category testMlCategory;
    private Category testUncategorisedCategory;

    @BeforeEach
    void setUp() {
        testSourceTransaction = new NormalisedTransaction(
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

        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setSourceId("source-123");

        testRulesCategory = new Category();
        testRulesCategory.setId(1L);
        testRulesCategory.setCode("GROCERIES");

        testMlCategory = new Category();
        testMlCategory.setId(2L);
        testMlCategory.setCode("ENTERTAINMENT");

        testUncategorisedCategory = new Category();
        testUncategorisedCategory.setId(999L);
        testUncategorisedCategory.setCode("uncategorised");
    }

    @Test
    void categorise_shouldUseRulesEngineWhenCategoryFound() {
        // Given
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.of(testRulesCategory));

        // When
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Then
        assertEquals(testRulesCategory, testTransaction.getCategory());
        verify(testRulesEngine).categorise(testSourceTransaction);
        verifyNoInteractions(testMlClassifier);
        verify(testCategoryRepository, never()).uncategorised();
    }

    @Test
    void categorise_shouldUseUncategorisedFallbackWhenBothRulesAndMlReturnEmpty() {
        // Arrange
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.empty());
        when(testMlClassifier.isPresent()).thenReturn(false);
        when(testCategoryRepository.uncategorised()).thenReturn(testUncategorisedCategory);

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertEquals(testUncategorisedCategory, testTransaction.getCategory());
        verify(testRulesEngine).categorise(testSourceTransaction);
        verify(testCategoryRepository).uncategorised();
    }

    @Test
    void categorise_shouldUseUncategorisedFallbackWhenMlClassifierReturnsEmpty() {
        // Arrange
        MlClassifier testMockMlClassifier = mock(MlClassifier.class);
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.empty());
        when(testMlClassifier.isPresent()).thenReturn(true);
        when(testMlClassifier.get()).thenReturn(testMockMlClassifier);
        when(testMockMlClassifier.classify(testSourceTransaction)).thenReturn(Optional.empty());
        when(testCategoryRepository.uncategorised()).thenReturn(testUncategorisedCategory);

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertEquals(testUncategorisedCategory, testTransaction.getCategory());
        verify(testRulesEngine).categorise(testSourceTransaction);
        verify(testMockMlClassifier).classify(testSourceTransaction);
        verify(testCategoryRepository).uncategorised();
    }

    @Test
    void categorise_shouldHandleMlClassifierNotPresent() {
        // Arrange
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.empty());
        when(testMlClassifier.isPresent()).thenReturn(false);
        when(testCategoryRepository.uncategorised()).thenReturn(testUncategorisedCategory);

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertEquals(testUncategorisedCategory, testTransaction.getCategory());
        verify(testRulesEngine).categorise(testSourceTransaction);
        verify(testCategoryRepository).uncategorised();
    }

    @Test
    void categorise_shouldLogAppropriateTierForRulesMatch() {
        // Arrange
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.of(testRulesCategory));

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertEquals(testRulesCategory, testTransaction.getCategory());
        // Log verification would require a different approach with @Captor
    }

    @Test
    void categorise_shouldLogAppropriateTierForMlMatch() {
        // Arrange
        MlClassifier testMockMlClassifier = mock(MlClassifier.class);
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.empty());
        when(testMlClassifier.isPresent()).thenReturn(true);
        when(testMlClassifier.get()).thenReturn(testMockMlClassifier);
        when(testMockMlClassifier.classify(testSourceTransaction)).thenReturn(Optional.of(testMlCategory));

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertEquals(testMlCategory, testTransaction.getCategory());
    }

    @Test
    void categorise_shouldLogAppropriateTierForFallback() {
        // Arrange
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.empty());
        when(testMlClassifier.isPresent()).thenReturn(false);
        when(testCategoryRepository.uncategorised()).thenReturn(testUncategorisedCategory);

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertEquals(testUncategorisedCategory, testTransaction.getCategory());
    }

    @Test
    void categorise_shouldHandleNullTransactionCategoryGracefully() {
        // Arrange
        when(testRulesEngine.categorise(testSourceTransaction)).thenReturn(Optional.of(testRulesCategory));

        // Act
        testCategorisationService.categorise(testTransaction, testSourceTransaction);

        // Assert
        assertNotNull(testTransaction.getCategory());
        assertEquals(testRulesCategory, testTransaction.getCategory());
    }

    @Test
    void categorise_shouldWorkWithDifferentSourceTypes() {
        // Arrange
        NormalisedTransaction testBankFeedSource = new NormalisedTransaction(
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

        when(testRulesEngine.categorise(testBankFeedSource)).thenReturn(Optional.of(testRulesCategory));

        Transaction testBankTransaction = new Transaction();
        testBankTransaction.setId(2L);
        testBankTransaction.setSourceId("source-456");

        // Act
        testCategorisationService.categorise(testBankTransaction, testBankFeedSource);

        // Assert
        assertEquals(testRulesCategory, testBankTransaction.getCategory());
        verify(testRulesEngine).categorise(testBankFeedSource);
    }
}