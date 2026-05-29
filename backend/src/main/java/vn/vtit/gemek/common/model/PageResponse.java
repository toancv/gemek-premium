/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.model;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standard paginated response wrapper.
 *
 * <p>Wraps a Spring {@link Page} into the contract shape defined in the API specification:
 * <pre>
 * {
 *   "data":       [],
 *   "page":       0,
 *   "size":       20,
 *   "total":      100,
 *   "totalPages": 5
 * }
 * </pre>
 *
 * @param <T> the type of items in the page.
 */
@Getter
public class PageResponse<T> {

    /** The list of items on the current page. */
    private final List<T> data;

    /** The 0-based page index. */
    private final int page;

    /** The number of items per page. */
    private final int size;

    /** The total number of items across all pages. */
    private final long total;

    /** The total number of pages. */
    private final int totalPages;

    /**
     * Constructs a {@link PageResponse} from a Spring {@link Page}.
     *
     * @param springPage the Spring page result to wrap.
     */
    private PageResponse(Page<T> springPage) {
        this.data = springPage.getContent();
        this.page = springPage.getNumber();
        this.size = springPage.getSize();
        this.total = springPage.getTotalElements();
        this.totalPages = springPage.getTotalPages();
    }

    /**
     * Factory method wrapping a Spring {@link Page} into a {@link PageResponse}.
     *
     * @param springPage the Spring page to wrap.
     * @param <T>        the item type.
     * @return a {@link PageResponse} containing the page data and metadata.
     */
    public static <T> PageResponse<T> of(Page<T> springPage) {
        return new PageResponse<>(springPage);
    }
}
