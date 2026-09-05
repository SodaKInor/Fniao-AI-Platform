package org.jeecg.modules.ai.asset.persistence.mapper;

import org.apache.ibatis.annotations.*;
import org.jeecg.modules.ai.asset.persistence.entity.AssetRow;

public interface AssetMapper {
    @Insert("INSERT INTO ai_asset(asset_id,owner_id,file_name,media_type,storage_key,size_bytes,sha256,created_at,expires_at) "
            + "VALUES(#{assetId},#{ownerId},#{fileName},#{mediaType},#{storageKey},#{sizeBytes},#{sha256},#{createdAt},#{expiresAt})")
    void insert(AssetRow row);

    @Select("SELECT asset_id AS assetId,owner_id AS ownerId,file_name AS fileName,media_type AS mediaType,"
            + "storage_key AS storageKey,size_bytes AS sizeBytes,sha256,created_at AS createdAt,expires_at AS expiresAt "
            + "FROM ai_asset WHERE asset_id=#{id} AND owner_id=#{owner}")
    AssetRow findOwned(@Param("id") String id, @Param("owner") String owner);
}
