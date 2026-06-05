/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.dto.BlockResponse;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateBlockRequest;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.mapper.BlockMapper;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link BlockService} for building block management.
 *
 * <p>Enforces name uniqueness and prevents deletion of blocks that have apartments.
 * The list endpoint supports optional name search via the Criteria API to avoid
 * the Hibernate-6 nullable JPQL {@code LIKE} issue on PostgreSQL.
 * All mutating methods are transactional.
 */
@Service
@Transactional(readOnly = true)
public class BlockServiceImpl implements BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockServiceImpl.class);

    private final BlockRepository blockRepository;
    private final ApartmentRepository apartmentRepository;
    private final BlockMapper blockMapper;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param blockRepository     the block JPA repository.
     * @param apartmentRepository the apartment JPA repository (used for deletion check).
     * @param blockMapper         the MapStruct block mapper.
     */
    public BlockServiceImpl(BlockRepository blockRepository,
                            ApartmentRepository apartmentRepository,
                            BlockMapper blockMapper) {
        this.blockRepository = blockRepository;
        this.apartmentRepository = apartmentRepository;
        this.blockMapper = blockMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<BlockResponse> listBlocks(String search, Pageable pageable) {
        log.debug("Listing blocks — search={}", search);

        // Build Specification via Criteria API — avoids Hibernate-6 null→bytea bug with JPQL LIKE.
        Specification<Block> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<BlockResponse> page = blockRepository.findAll(spec, pageable).map(blockMapper::toResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BlockResponse createBlock(CreateBlockRequest request) {
        log.debug("Creating block — name={}", request.name());

        // Name uniqueness check before insert for a clear conflict message.
        if (blockRepository.existsByName(request.name())) {
            throw new AppException(ErrorCode.CONFLICT,
                    "A block with name '" + request.name() + "' already exists.");
        }

        Block block = new Block();
        block.setName(request.name());
        block.setDescription(request.description());

        Block saved = blockRepository.save(block);
        log.info("Block created — id={}, name={}", saved.getId(), saved.getName());
        return blockMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BlockResponse updateBlock(UUID id, UpdateBlockRequest request) {
        log.debug("Updating block id={}", id);
        Block block = findOrThrow(id);

        // Check if another block already uses the requested name.
        if (blockRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "A block with name '" + request.name() + "' already exists.");
        }

        block.setName(request.name());
        block.setDescription(request.description());

        Block saved = blockRepository.save(block);
        log.info("Block updated — id={}, name={}", saved.getId(), saved.getName());
        return blockMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteBlock(UUID id) {
        log.debug("Deleting block id={}", id);
        Block block = findOrThrow(id);

        // Prevent deletion when the block has apartments to respect FK constraint.
        if (apartmentRepository.existsByBlockId(id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Block '" + block.getName() + "' has apartments and cannot be deleted.");
        }

        blockRepository.delete(block);
        log.info("Block deleted — id={}", id);
    }

    /**
     * Loads a block by UUID or throws a NOT_FOUND exception.
     *
     * @param id the block UUID.
     * @return the found {@link Block} entity.
     * @throws AppException with {@link ErrorCode#NOT_FOUND} if the block does not exist.
     */
    private Block findOrThrow(UUID id) {
        return blockRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Block not found: " + id));
    }
}
