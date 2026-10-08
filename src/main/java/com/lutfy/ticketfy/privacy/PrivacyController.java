package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.auth.RefreshTokenCookie;
import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class PrivacyController {

    private final DataExportService exportService;
    private final AccountDeletionService deletionService;
    private final RefreshTokenCookie cookie;

    public PrivacyController(DataExportService exportService, AccountDeletionService deletionService,
                             RefreshTokenCookie cookie) {
        this.exportService = exportService;
        this.deletionService = deletionService;
        this.cookie = cookie;
    }

    @PostMapping("/data-export")
    public ResponseEntity<DataExportDTO> export(@AuthenticationPrincipal User user,
                                                @RequestBody @Valid DataExportRequestDTO dto) {
        var export = exportService.export(user, dto);
        var filename = "ticketfy-meus-dados-" + exportService.today() + ".json";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(export);
    }

    @PostMapping("/deletion")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user,
                                       @RequestBody @Valid AccountDeletionRequestDTO dto) {
        deletionService.delete(user, dto);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }
}
