package za.co.reed.processingservice.categorisation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.commom.enums.SourceType;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.persistence.entity.Category;
import za.co.reed.persistence.repository.CategoryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulesEngineTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private RulesEngine rulesEngine;

    private NormalisedTransaction transactionWithMcc;
    private NormalisedTransaction transactionWithoutMcc;
    private NormalisedTransaction transactionWithEmptyMerchant;
    private Category groceriesCategory;
    private Category restaurantsCategory;

    @BeforeEach
    void setUp() {
        transactionWithMcc = new NormalisedTransaction(
                "source-123",
                SourceType.CARD_NETWORK,
                "ACC-001",
                BigDecimal.valueOf(100.50),
                java.util.Currency.getInstance("ZAR"),
                "Test Supermarket",
                "5411", // Groceries MCC
                Instant.now(),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        transactionWithoutMcc = new NormalisedTransaction(
                "source-456",
                SourceType.BANK_FEED,
                "ACC-002",
                BigDecimal.valueOf(200.75),
                java.util.Currency.getInstance("USD"),
                "Starbucks Coffee",
                null, // No MCC
                Instant.now(),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        transactionWithEmptyMerchant = new NormalisedTransaction(
                "source-789",
                SourceType.PAYMENT_PROCESSOR,
                "ACC-003",
                BigDecimal.valueOf(50.00),
                java.util.Currency.getInstance("EUR"),
                "Test Merchant", // Merchant name must not be blank
                null,
                Instant.now(),
                TransactionStatus.SETTLED,
                "{\"raw\": \"payload\"}"
        );

        groceriesCategory = Category.builder()
                .id(1L)
                .code("GROCERIES")
                .label("Groceries")
                .path("groceries")
                .mccCodes("5411,5422")
                .keywords("supermarket,grocery,store,food")
                .build();

        restaurantsCategory = Category.builder()
                .id(2L)
                .code("RESTAURANTS")
                .label("Restaurants")
                .path("restaurants")
                .mccCodes("5812,5814")
                .keywords("restaurant,cafe,coffee,starbucks")
                .build();
    }

    @Test
    void categorise_shouldReturnCategoryWhenMccMatches() {
        // Arrange
        when(categoryRepository.findByMccCodesLike("5411")).thenReturn(List.of(groceriesCategory));

        // Act
        Optional<Category> result = rulesEngine.categorise(transactionWithMcc);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(groceriesCategory, result.get());
        verify(categoryRepository).findByMccCodesLike("5411");
        verifyNoMoreInteractions(categoryRepository);
    }

    @Test
    void categorise_shouldReturnFirstCategoryWhenMultipleMccMatches() {
        // Arrange
        Category anotherCategory = Category.builder()
                .id(3L)
                .code("FOOD")
                .label("Food")
                .path("food")
                .mccCodes("5411,5499")
                .build();
        
        when(categoryRepository.findByMccCodesLike("5411")).thenReturn(Arrays.asList(groceriesCategory, anotherCategory));

        // Act
        Optional<Category> result = rulesEngine.categorise(transactionWithMcc);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(groceriesCategory, result.get()); // Should return first match
        verify(categoryRepository).findByMccCodesLike("5411");
    }

    @Test
    void categorise_shouldReturnEmptyWhenNoMccMatch() {
        // Arrange
        when(categoryRepository.findByMccCodesLike("5411")).thenReturn(List.of());

        // Act
        Optional<Category> result = rulesEngine.categorise(transactionWithMcc);

        // Assert
        assertFalse(result.isPresent());
        verify(categoryRepository).findByMccCodesLike("5411");
        verify(categoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void categorise_shouldFallbackToKeywordMatchingWhenNoMcc() {
        // Arrange
        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(restaurantsCategory));

        // Act
        Optional<Category> result = rulesEngine.categorise(transactionWithoutMcc);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(restaurantsCategory, result.get());
        verify(categoryRepository, never()).findByMccCodesLike(anyString());
        verify(categoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void categorise_shouldReturnEmptyWhenNoKeywordMatch() {
        // Arrange
        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(groceriesCategory));

        // Act
        Optional<Category> result = rulesEngine.categorise(transactionWithoutMcc);

        // Assert
        assertFalse(result.isPresent());
        verify(categoryRepository, never()).findByMccCodesLike(anyString());
        verify(categoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void categorise_shouldReturnEmptyWhenNoMatchingKeywords() {
        // Arrange - transactionWithEmptyMerchant now has "Test Merchant" as merchant name
        // groceriesCategory doesn't have keywords matching "Test Merchant"
        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(groceriesCategory));

        // Act
        Optional<Category> result = rulesEngine.categorise(transactionWithEmptyMerchant);

        // Assert
        assertFalse(result.isPresent());
        verify(categoryRepository, never()).findByMccCodesLike(anyString());
        verify(categoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void matchByMcc_shouldReturnCategoryWhenMccFound() {
        // Arrange
        when(categoryRepository.findByMccCodesLike("5411")).thenReturn(List.of(groceriesCategory));

        // Act
        Optional<Category> result = rulesEngine.matchByMcc("5411");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(groceriesCategory, result.get());
        verify(categoryRepository).findByMccCodesLike("5411");
    }

    @Test
    void matchByMcc_shouldReturnEmptyWhenNoMccFound() {
        // Arrange
        when(categoryRepository.findByMccCodesLike("9999")).thenReturn(List.of());

        // Act
        Optional<Category> result = rulesEngine.matchByMcc("9999");

        // Assert
        assertFalse(result.isPresent());
        verify(categoryRepository).findByMccCodesLike("9999");
    }

    @Test
    void matchByMcc_shouldCacheResults() {
        // Arrange
        when(categoryRepository.findByMccCodesLike("5411")).thenReturn(List.of(groceriesCategory));

        // Act - call twice
        Optional<Category> result1 = rulesEngine.matchByMcc("5411");
        Optional<Category> result2 = rulesEngine.matchByMcc("5411");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(groceriesCategory, result1.get());
        assertEquals(groceriesCategory, result2.get());
    }

    @Test
    void matchByKeyword_shouldReturnCategoryWhenKeywordMatches() {
        // Arrange
        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(restaurantsCategory));

        // Act
        Optional<Category> result = rulesEngine.matchByKeyword("Starbucks Coffee");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(restaurantsCategory, result.get());
        verify(categoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void matchByKeyword_shouldReturnEmptyWhenMerchantNameIsEmpty() {
        // Act
        Optional<Category> result = rulesEngine.matchByKeyword("");

        // Assert
        assertFalse(result.isPresent());
        verify(categoryRepository, never()).findAllByKeywordsNotNull();
    }


    @Test
    void matchByKeyword_shouldBeCaseInsensitive() {
        // Arrange
        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(restaurantsCategory));

        // Act
        Optional<Category> result1 = rulesEngine.matchByKeyword("STARBUCKS COFFEE");
        Optional<Category> result2 = rulesEngine.matchByKeyword("starbucks coffee");
        Optional<Category> result3 = rulesEngine.matchByKeyword("StArBuCkS CoFfEe");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertTrue(result3.isPresent());
        assertEquals(restaurantsCategory, result1.get());
        assertEquals(restaurantsCategory, result2.get());
        assertEquals(restaurantsCategory, result3.get());
    }

    @Test
    void matchByKeyword_shouldCacheResults() {
        // Arrange
        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(restaurantsCategory));

        // Act - call twice with same merchant name
        Optional<Category> result1 = rulesEngine.matchByKeyword("Starbucks");
        Optional<Category> result2 = rulesEngine.matchByKeyword("Starbucks");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(restaurantsCategory, result1.get());
        assertEquals(restaurantsCategory, result2.get());
    }

    @Test
    void matchByKeyword_shouldMatchPartialKeywords() {
        // Arrange
        Category categoryWithPartial = Category.builder()
                .id(4L)
                .code("TRAVEL")
                .label("Travel")
                .path("travel")
                .keywords("airline,flight,airport")
                .build();

        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(categoryWithPartial));

        // Act
        Optional<Category> result = rulesEngine.matchByKeyword("Southwest Airlines Booking");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(categoryWithPartial, result.get());
        verify(categoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void matchByKeyword_shouldHandleMultipleKeywordsInCategory() {
        // Arrange
        Category multiKeywordCategory = Category.builder()
                .id(5L)
                .code("SHOPPING")
                .label("Shopping")
                .path("shopping")
                .keywords("mall,store,shop,boutique,retail")
                .build();

        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(multiKeywordCategory));

        // Act - test different keyword matches
        Optional<Category> result1 = rulesEngine.matchByKeyword("Shopping Mall");
        Optional<Category> result2 = rulesEngine.matchByKeyword("Retail Store");
        Optional<Category> result3 = rulesEngine.matchByKeyword("Boutique Shop");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertTrue(result3.isPresent());
        assertEquals(multiKeywordCategory, result1.get());
        assertEquals(multiKeywordCategory, result2.get());
        assertEquals(multiKeywordCategory, result3.get());
    }

    @Test
    void matchByKeyword_shouldHandleKeywordsWithSpaces() {
        // Arrange
        Category categoryWithSpacedKeywords = Category.builder()
                .id(6L)
                .code("HOME_IMPROVEMENT")
                .label("Home Improvement")
                .path("home_improvement")
                .keywords("home depot,lowes,hardware store")
                .build();

        when(categoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(categoryWithSpacedKeywords));

        // Act
        Optional<Category> result = rulesEngine.matchByKeyword("Purchase at Home Depot");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(categoryWithSpacedKeywords, result.get());
        verify(categoryRepository).findAllByKeywordsNotNull();
    }
}