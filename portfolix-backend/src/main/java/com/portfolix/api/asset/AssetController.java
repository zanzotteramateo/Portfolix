package com.portfolix.api.asset;

import com.portfolix.api.asset.dto.AssetResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public List<AssetResponse> list(@RequestParam(required = false) AssetType type,
                                    @RequestParam(name = "q", required = false) String query) {
        return assetService.listActive(type, query);
    }
}
