package com.fu.math_copilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fu.math_copilot.model.entity.ExportTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * 导出任务 Mapper
 */
public interface ExportTaskMapper extends BaseMapper<ExportTask> {

    @Update("UPDATE export_task SET status = 'processing', progress = 10, errorMsg = NULL, " +
            "updateTime = NOW() WHERE id = #{taskId} AND status = 'pending'")
    int claimPending(@Param("taskId") Long taskId);

    @Update("UPDATE export_task SET status = 'pending', progress = 0, retryCount = retryCount + 1, " +
            "errorMsg = #{errorMsg}, updateTime = NOW() " +
            "WHERE id = #{taskId} AND status = 'processing'")
    int rescheduleFromProcessing(@Param("taskId") Long taskId,
                                 @Param("errorMsg") String errorMsg);

    @Update("UPDATE export_task SET status = 'failed', retryCount = retryCount + 1, " +
            "errorMsg = #{errorMsg}, updateTime = NOW() " +
            "WHERE id = #{taskId} AND status = 'processing'")
    int markFailedFromProcessing(@Param("taskId") Long taskId,
                                 @Param("errorMsg") String errorMsg);

    @Select("SELECT id FROM export_task WHERE status = #{status} AND updateTime < #{before} " +
            "ORDER BY updateTime ASC LIMIT #{limit}")
    List<Long> findIdsByStatusBefore(@Param("status") String status,
                                     @Param("before") Date before,
                                     @Param("limit") int limit);

    @Update("UPDATE export_task SET status = 'pending', progress = 0, updateTime = NOW() " +
            "WHERE id = #{taskId} AND status = 'processing' AND updateTime < #{before}")
    int resetTimedOutProcessing(@Param("taskId") Long taskId,
                                @Param("before") Date before);

    @Update("UPDATE export_task SET updateTime = NOW() WHERE id = #{taskId} AND status = 'pending'")
    int touchPendingDispatch(@Param("taskId") Long taskId);
}
