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
    private CategoryRepository testCategoryRepository;

    @InjectMocks
    private RulesEngine testRulesEngine;

    private NormalisedTransaction testTransactionWithMcc;
    private NormalisedTransaction testTransactionWithoutMcc;
    private NormalisedTransaction testTransactionWithEmptyMerchant;
    private Category testGroceriesCategory;
    private Category testRestaurantsCategory;

    @BeforeEach
    void setUp() {
        testTransactionWithMcc = new NormalisedTransaction(
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

        testTransactionWithoutMcc = new NormalisedTransaction(
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

        testTransactionWithEmptyMerchant = new NormalisedTransaction(
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

        testGroceriesCategory = Category.builder()
                .id(1L)
                .code("GROCERIES")
                .label("Groceries")
                .path("groceries")
                .mccCodes("5411,5422")
                .keywords("supermarket,grocery,store,food")
                .build();

        testRestaurantsCategory = Category.builder()
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
        when(testCategoryRepository.findByMccCodesLike("5411")).thenReturn(List.of(testGroceriesCategory));

        // Act
        Optional<Category> result = testRulesEngine.categorise(testTransactionWithMcc);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testGroceriesCategory, result.get());
        verify(testCategoryRepository).findByMccCodesLike("5411");
        verifyNoMoreInteractions(testCategoryRepository);
    }

    @Test
    void categorise_shouldReturnFirstCategoryWhenMultipleMccMatches() {
        // Arrange
        Category testAnotherCategory = Category.builder()
                .id(3L)
                .code("FOOD")
                .label("Food")
                .path("food")
                .mccCodes("5411,5499")
                .build();
        
        when(testCategoryRepository.findByMccCodesLike("5411")).thenReturn(Arrays.asList(testGroceriesCategory, testAnotherCategory));

        // Act
        Optional<Category> result = testRulesEngine.categorise(testTransactionWithMcc);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testGroceriesCategory, result.get()); // Should return first match
        verify(testCategoryRepository).findByMccCodesLike("5411");
    }

    @Test
    void categorise_shouldReturnEmptyWhenNoMccMatch() {
        // Arrange
        when(testCategoryRepository.findByMccCodesLike("5411")).thenReturn(List.of());

        // Act
        Optional<Category> result = testRulesEngine.categorise(testTransactionWithMcc);

        // Assert
        assertFalse(result.isPresent());
        verify(testCategoryRepository).findByMccCodesLike("5411");
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void categorise_shouldFallbackToKeywordMatchingWhenNoMcc() {
        // Arrange
        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testRestaurantsCategory));

        // Act
        Optional<Category> result = testRulesEngine.categorise(testTransactionWithoutMcc);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testRestaurantsCategory, result.get());
        verify(testCategoryRepository, never()).findByMccCodesLike(anyString());
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void categorise_shouldReturnEmptyWhenNoKeywordMatch() {
        // Arrange
        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testGroceriesCategory));

        // Act
        Optional<Category> result = testRulesEngine.categorise(testTransactionWithoutMcc);

        // Assert
        assertFalse(result.isPresent());
        verify(testCategoryRepository, never()).findByMccCodesLike(anyString());
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void categorise_shouldReturnEmptyWhenNoMatchingKeywords() {
        // Arrange - testTransactionWithEmptyMerchant now has "Test Merchant" as merchant name
        // testGroceriesCategory doesn't have keywords matching "Test Merchant"
        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testGroceriesCategory));

        // Act
        Optional<Category> result = testRulesEngine.categorise(testTransactionWithEmptyMerchant);

        // Assert
        assertFalse(result.isPresent());
        verify(testCategoryRepository, never()).findByMccCodesLike(anyString());
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void matchByMcc_shouldReturnCategoryWhenMccFound() {
        // Arrange
        when(testCategoryRepository.findByMccCodesLike("5411")).thenReturn(List.of(testGroceriesCategory));

        // Act
        Optional<Category> result = testRulesEngine.matchByMcc("5411");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testGroceriesCategory, result.get());
        verify(testCategoryRepository).findByMccCodesLike("5411");
    }

    @Test
    void matchByMcc_shouldReturnEmptyWhenNoMccFound() {
        // Arrange
        when(testCategoryRepository.findByMccCodesLike("9999")).thenReturn(List.of());

        // Act
        Optional<Category> result = testRulesEngine.matchByMcc("9999");

        // Assert
        assertFalse(result.isPresent());
        verify(testCategoryRepository).findByMccCodesLike("9999");
    }

    @Test
    void matchByMcc_shouldCacheResults() {
        // Arrange
        when(testCategoryRepository.findByMccCodesLike("5411")).thenReturn(List.of(testGroceriesCategory));

        // Act - call twice
        Optional<Category> result1 = testRulesEngine.matchByMcc("5411");
        Optional<Category> result2 = testRulesEngine.matchByMcc("5411");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(testGroceriesCategory, result1.get());
        assertEquals(testGroceriesCategory, result2.get());
    }

    @Test
    void matchByKeyword_shouldReturnCategoryWhenKeywordMatches() {
        // Arrange
        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testRestaurantsCategory));

        // Act
        Optional<Category> result = testRulesEngine.matchByKeyword("Starbucks Coffee");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testRestaurantsCategory, result.get());
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void matchByKeyword_shouldReturnEmptyWhenMerchantNameIsEmpty() {
        // Act
        Optional<Category> result = testRulesEngine.matchByKeyword("");

        // Assert
        assertFalse(result.isPresent());
        verify(testCategoryRepository, never()).findAllByKeywordsNotNull();
    }


    @Test
    void matchByKeyword_shouldBeCaseInsensitive() {
        // Arrange
        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testRestaurantsCategory));

        // Act
        Optional<Category> result1 = testRulesEngine.matchByKeyword("STARBUCKS COFFEE");
        Optional<Category> result2 = testRulesEngine.matchByKeyword("starbucks coffee");
        Optional<Category> result3 = testRulesEngine.matchByKeyword("StArBuCkS CoFfEe");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertTrue(result3.isPresent());
        assertEquals(testRestaurantsCategory, result1.get());
        assertEquals(testRestaurantsCategory, result2.get());
        assertEquals(testRestaurantsCategory, result3.get());
    }

    @Test
    void matchByKeyword_shouldCacheResults() {
        // Arrange
        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testRestaurantsCategory));

        // Act - call twice with same merchant name
        Optional<Category> result1 = testRulesEngine.matchByKeyword("Starbucks");
        Optional<Category> result2 = testRulesEngine.matchByKeyword("Starbucks");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(testRestaurantsCategory, result1.get());
        assertEquals(testRestaurantsCategory, result2.get());
    }

    @Test
    void matchByKeyword_shouldMatchPartialKeywords() {
        // Arrange
        Category testCategoryWithPartial = Category.builder()
                .id(4L)
                .code("TRAVEL")
                .label("Travel")
                .path("travel")
                .keywords("airline,flight,airport")
                .build();

        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testCategoryWithPartial));

        // Act
        Optional<Category> result = testRulesEngine.matchByKeyword("Southwest Airlines Booking");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testCategoryWithPartial, result.get());
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }

    @Test
    void matchByKeyword_shouldHandleMultipleKeywordsInCategory() {
        // Arrange
        Category testMultiKeywordCategory = Category.builder()
                .id(5L)
                .code("SHOPPING")
                .label("Shopping")
                .path("shopping")
                .keywords("mall,store,shop,boutique,retail")
                .build();

        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testMultiKeywordCategory));

        // Act - test different keyword matches
        Optional<Category> result1 = testRulesEngine.matchByKeyword("Shopping Mall");
        Optional<Category> result2 = testRulesEngine.matchByKeyword("Retail Store");
        Optional<Category> result3 = testRulesEngine.matchByKeyword("Boutique Shop");

        // Assert
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertTrue(result3.isPresent());
        assertEquals(testMultiKeywordCategory, result1.get());
        assertEquals(testMultiKeywordCategory, result2.get());
        assertEquals(testMultiKeywordCategory, result3.get());
    }

    @Test
    void matchByKeyword_shouldHandleKeywordsWithSpaces() {
        // Arrange
        Category testCategoryWithSpacedKeywords = Category.builder()
                .id(6L)
                .code("HOME_IMPROVEMENT")
                .label("Home Improvement")
                .path("home_improvement")
                .keywords("home depot,lowes,hardware store")
                .build();

        when(testCategoryRepository.findAllByKeywordsNotNull()).thenReturn(List.of(testCategoryWithSpacedKeywords));

        // Act
        Optional<Category> result = testRulesEngine.matchByKeyword("Purchase at Home Depot");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testCategoryWithSpacedKeywords, result.get());
        verify(testCategoryRepository).findAllByKeywordsNotNull();
    }
}