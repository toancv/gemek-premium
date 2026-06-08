/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PhoneUtils}.
 *
 * <p>Each valid input row maps to the canonical form {@code 0962464748}.
 * All rejection cases must throw {@link AppException} with
 * {@link ErrorCode#VALIDATION_ERROR}.
 */
@DisplayName("PhoneUtils")
class PhoneUtilsTest {

    private static final String CANONICAL = "0962464748";

    // -------------------------------------------------------------------------
    // Accepted inputs — all must normalize to CANONICAL
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("already canonical — returned unchanged")
    void normalize_alreadyCanonical() {
        assertThat(PhoneUtils.normalize("0962464748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("+84 prefix — strip +84, prepend 0")
    void normalize_plus84Prefix() {
        assertThat(PhoneUtils.normalize("+84962464748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("84 prefix (no +) — strip 84, prepend 0")
    void normalize_84PrefixNoPlus() {
        assertThat(PhoneUtils.normalize("84962464748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("+840 prefix — strip +84, remainder starts with 0, no double-zero")
    void normalize_plus840Prefix() {
        assertThat(PhoneUtils.normalize("+840962464748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("840 prefix (no +) — strip 84, remainder already has leading 0")
    void normalize_840PrefixNoPlus() {
        assertThat(PhoneUtils.normalize("840962464748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("spaces in input — stripped before processing")
    void normalize_spacesStripped() {
        assertThat(PhoneUtils.normalize("096 246 4748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("+84 with spaces — spaces stripped then prefix normalized")
    void normalize_plus84WithSpaces() {
        assertThat(PhoneUtils.normalize("+84 96 246 4748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("dots in input — stripped before processing")
    void normalize_dotsStripped() {
        assertThat(PhoneUtils.normalize("0962.464.748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("hyphens in input — stripped before processing")
    void normalize_hyphensStripped() {
        assertThat(PhoneUtils.normalize("0962-464-748")).isEqualTo(CANONICAL);
    }

    @Test
    @DisplayName("mixed separators — all stripped")
    void normalize_mixedSeparators() {
        assertThat(PhoneUtils.normalize("096 246-4748")).isEqualTo(CANONICAL);
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("idempotent — normalize(normalize(x)) == normalize(x) for canonical input")
    void normalize_idempotent_canonical() {
        String once = PhoneUtils.normalize(CANONICAL);
        assertThat(PhoneUtils.normalize(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("idempotent — normalize(normalize(x)) == normalize(x) for +84 input")
    void normalize_idempotent_plus84() {
        String once = PhoneUtils.normalize("+84962464748");
        assertThat(PhoneUtils.normalize(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("idempotent — normalize(normalize(x)) == normalize(x) for 84 input")
    void normalize_idempotent_84() {
        String once = PhoneUtils.normalize("84962464748");
        assertThat(PhoneUtils.normalize(once)).isEqualTo(once);
    }

    // -------------------------------------------------------------------------
    // Various valid prefixes — ensure other VN mobile prefixes pass
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"0312345678", "0412345678", "0512345678",
            "0612345678", "0712345678", "0812345678", "0912345678"})
    @DisplayName("VN mobile prefixes 03–09 — all accepted")
    void normalize_vnMobilePrefixRange(String phone) {
        assertThat(PhoneUtils.normalize(phone)).isEqualTo(phone);
    }

    // -------------------------------------------------------------------------
    // Reject cases — must throw VALIDATION_ERROR
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null or empty — throws VALIDATION_ERROR")
    void normalize_nullOrEmpty_throws(String input) {
        assertThatThrownBy(() -> PhoneUtils.normalize(input))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("blank (spaces only) — throws VALIDATION_ERROR")
    void normalize_blankSpaces_throws() {
        assertThatThrownBy(() -> PhoneUtils.normalize("   "))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("01xxxxxxxx (old 11-digit prefix) — throws VALIDATION_ERROR")
    void normalize_oldPrefix01_throws() {
        // 0123456789 starts with 01 — not a valid mobile prefix post-2018
        assertThatThrownBy(() -> PhoneUtils.normalize("0123456789"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("02xxxxxxxx (landline area code) — throws VALIDATION_ERROR")
    void normalize_landline_throws() {
        assertThatThrownBy(() -> PhoneUtils.normalize("02838221234"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("9 digits — too short, throws VALIDATION_ERROR")
    void normalize_tooShort_throws() {
        assertThatThrownBy(() -> PhoneUtils.normalize("096246474"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("11 digits — too long, throws VALIDATION_ERROR")
    void normalize_tooLong_throws() {
        assertThatThrownBy(() -> PhoneUtils.normalize("09624647480"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("letters in input — throws VALIDATION_ERROR")
    void normalize_letters_throws() {
        assertThatThrownBy(() -> PhoneUtils.normalize("abc"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("alphanumeric mix — throws VALIDATION_ERROR")
    void normalize_alphanumericMix_throws() {
        assertThatThrownBy(() -> PhoneUtils.normalize("096abc4748"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // -------------------------------------------------------------------------
    // isValid — non-throwing mirror
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isValid returns true for canonical input")
    void isValid_canonical_true() {
        assertThat(PhoneUtils.isValid("0962464748")).isTrue();
    }

    @Test
    @DisplayName("isValid returns true for +84 input")
    void isValid_plus84_true() {
        assertThat(PhoneUtils.isValid("+84962464748")).isTrue();
    }

    @Test
    @DisplayName("isValid returns false for null")
    void isValid_null_false() {
        assertThat(PhoneUtils.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for empty string")
    void isValid_empty_false() {
        assertThat(PhoneUtils.isValid("")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for invalid input")
    void isValid_invalid_false() {
        assertThat(PhoneUtils.isValid("abc")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for landline")
    void isValid_landline_false() {
        assertThat(PhoneUtils.isValid("02838221234")).isFalse();
    }
}
