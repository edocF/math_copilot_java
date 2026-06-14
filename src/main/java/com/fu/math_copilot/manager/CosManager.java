package com.fu.math_copilot.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.fu.math_copilot.config.CosClientConfig;
import com.fu.math_copilot.constant.FileConstant;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.net.URL;
import java.util.Date;

/**
 * Cos 对象存储操作
 *
 * @author <a href="https://github.com/edocF">edocF</a>
 * @from <a href="https://fu.icu"></a>
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key 唯一键
     * @param localFilePath 本地文件路径
     * @return
     */
    public PutObjectResult putObject(String key, String localFilePath) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                new File(localFilePath));
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传对象
     *
     * @param key 唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 构建可下载 URL：公有桶直链，私有桶预签名
     */
    public String buildDownloadUrl(String key) {
        String normalizedKey = normalizeKey(key);
        if (cosClientConfig.isPublicAccess()) {
            return FileConstant.COS_HOST + normalizedKey;
        }
        Date expiration = new Date(System.currentTimeMillis()
                + cosClientConfig.getPresignedUrlExpireSeconds() * 1000L);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                cosClientConfig.getBucket(), normalizedKey, HttpMethodName.GET);
        request.setExpiration(expiration);
        URL url = cosClient.generatePresignedUrl(request);
        return url.toString();
    }

    private String normalizeKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return key.startsWith("/") ? key : "/" + key;
    }
}
