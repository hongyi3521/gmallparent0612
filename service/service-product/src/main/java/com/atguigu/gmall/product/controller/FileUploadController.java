package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import org.apache.commons.io.FilenameUtils;
import org.csource.common.MyException;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient1;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("admin/product/")
public class FileUploadController {
    @Value("${fileServer.url}")
    private String fileServerUrl;

//    @PostMapping("fileUpload")
//    public Result fileUpload(MultipartFile file) throws MyException, IOException {
//        // 根据配置文件获取文件信息
//        String configFile = this.getClass().getResource("/tracker.conf").getFile();
//        String path = null;
//        if(configFile != null){
//            // 初始化
//            ClientGlobal.init(configFile);
//            // 创建trackerClient,上传客户端
//            TrackerClient trackerClient = new TrackerClient();
//            // 获取trackerService,路由连接
//            TrackerServer trackerServer = trackerClient.getConnection();
//            // 创建storageClient1
//            StorageClient1 storageClient1 = new StorageClient1(trackerServer,null);
//            path = storageClient1.upload_appender_file1(file.getBytes(),
//                    FilenameUtils.getExtension(file.getOriginalFilename()), null);
//            System.out.println("打印路径  "+path);
//        }
//        return Result.ok(fileServerUrl+path);
//    }

    @PostMapping("fileUpload")
    public Result fileUpload(MultipartFile file) throws Exception {
        /*
        1.  加载配置文件tracker.conf
        2.  初始化当前文件
        3.  创建TrackerClient
        4.  创建TrackerServer
        5.  创建StorageClient1
        6.  文件上传
         */
        String configFile = this.getClass().getResource("/tracker.conf").getFile();
        String path = "";
        //  判断
        if(configFile!=null){
            //  初始化
            ClientGlobal.init(configFile);
            //  创建TrackerClient
            TrackerClient trackerClient = new TrackerClient();
            //  创建TrackerServer
            TrackerServer trackerServer = trackerClient.getConnection();
            //  创建StorageClient1
            StorageClient1 storageClient1 = new StorageClient1(trackerServer,null);
            //  文件上传
            //  获取到文件后缀名  zly.jpg  ---> xdfsfarwr1234554542as9082304.jpg
            String extName = FilenameUtils.getExtension(file.getOriginalFilename());
            //  获取到文件上传之后的url！
            path = storageClient1.upload_appender_file1(file.getBytes(), extName, null);
            //  group1/M00/00/01/wKjIgF9zVVGEavWOAAAAAO_LJ4k561.png
            System.out.println("文件上传之后的路径：\t"+path);
        }
        //  返回最终的路径！
        return Result.ok(fileServerUrl+path);
    }
}
