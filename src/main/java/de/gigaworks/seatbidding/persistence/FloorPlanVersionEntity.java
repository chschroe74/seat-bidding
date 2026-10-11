package de.gigaworks.seatbidding.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "floor_plan_version")
public class FloorPlanVersionEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_plan_id", nullable = false)
    public FloorPlanEntity floorPlan;

    @Column(name = "version_no", nullable = false)
    public long versionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_version_id")
    public FloorPlanVersionEntity baseVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public FloorPlanVersionStatus status;

    @Column(name = "draft_revision", nullable = false)
    public long draftRevision;

    @Column(name = "sanitized_svg", nullable = false, columnDefinition = "text")
    public String sanitizedSvg;

    @Column(name = "content_sha256", nullable = false, length = 64)
    public String contentSha256;

    @Column(name = "view_box_min_x", nullable = false)
    public BigDecimal viewBoxMinX;

    @Column(name = "view_box_min_y", nullable = false)
    public BigDecimal viewBoxMinY;

    @Column(name = "view_box_width", nullable = false)
    public BigDecimal viewBoxWidth;

    @Column(name = "view_box_height", nullable = false)
    public BigDecimal viewBoxHeight;

    @Column(name = "published_at")
    public Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_employee_id", nullable = false)
    public EmployeeEntity createdBy;
}