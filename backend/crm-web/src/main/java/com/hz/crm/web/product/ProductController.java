package com.hz.crm.web.product;

import com.hz.crm.application.product.ProductApplicationService;
import com.hz.crm.application.product.dto.ProductQuery;
import com.hz.crm.application.product.dto.ProductResponse;
import com.hz.crm.application.product.dto.ProductSaveRequest;
import com.hz.crm.auth.security.JwtPrincipal;
import com.hz.crm.common.api.ApiResult;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.audit.AuditOperation;
import com.hz.crm.web.support.IdRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductApplicationService productApplicationService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:product:view') "
            + "or hasAuthority('crm:channel:manage')")
    public ApiResult<PageData<ProductResponse>> pagePost(
            @RequestBody(required = false) ProductQuery query, JwtPrincipal principal) {
        return ApiResult.ok(productApplicationService.page(principal.getTenantId(), query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:product:view') "
            + "or hasAuthority('crm:channel:manage')")
    public ApiResult<ProductResponse> detailPost(@RequestBody IdRequest request, JwtPrincipal principal) {
        return ApiResult.ok(productApplicationService.detail(principal.getTenantId(), request.getId()));
    }

    @PostMapping("/options")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:product:view') "
            + "or hasAuthority('crm:product:manage') or hasAuthority('crm:channel:manage') "
            + "or hasAuthority('crm:lead:create') or hasAuthority('crm:lead:import') "
            + "or hasAuthority('crm:lead:manage') or hasAuthority('crm:customer:edit') "
            + "or hasAuthority('crm:customer:manage') or hasAuthority('crm:opportunity:create') "
            + "or hasAuthority('crm:opportunity:manage')")
    public ApiResult<List<ProductResponse>> options(JwtPrincipal principal) {
        return ApiResult.ok(productApplicationService.options(principal.getTenantId()));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:product:manage') "
            + "or (#request != null and #request.id == null and hasAuthority('crm:product:create'))")
    @AuditOperation(
            module = "PRODUCT",
            action = "SAVE",
            description = "保存产品",
            targetType = "PRODUCT")
    public ApiResult<ProductResponse> save(@Valid @RequestBody ProductSaveRequest request, JwtPrincipal principal) {
        return ApiResult.ok(productApplicationService.save(principal.getTenantId(), request));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('*') or hasAuthority('crm:product:manage')")
    @AuditOperation(
            module = "PRODUCT",
            action = "DELETE",
            description = "删除产品",
            targetType = "PRODUCT")
    public ApiResult<Void> delete(@RequestBody IdRequest request, JwtPrincipal principal) {
        productApplicationService.delete(principal.getTenantId(), request.getId());
        return ApiResult.ok(null);
    }
}
