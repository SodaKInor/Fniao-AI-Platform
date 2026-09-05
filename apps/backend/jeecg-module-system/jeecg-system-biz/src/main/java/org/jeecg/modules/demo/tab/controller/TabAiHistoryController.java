package org.jeecg.modules.demo.tab.controller;

import org.jeecg.modules.ai.legacy.LegacyExecutionGuard;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.demo.tab.entity.TabAiHistory;
import org.jeecg.modules.demo.tab.entity.TabAiModelBund;
import org.jeecg.modules.demo.tab.service.ITabAiHistoryService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import com.alibaba.fastjson.JSON;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.aspect.annotation.AutoLog;

 /**
 * @Description: AI识别结果历史
 * @Author: jeecg-boot
 * @Date:   2024-03-13
 * @Version: V1.0
 */
@Api(tags="AI识别结果历史")
@RestController
@RequestMapping("/tab/tabAiHistory")
@Slf4j
public class TabAiHistoryController extends JeecgController<TabAiHistory, ITabAiHistoryService> {
	@Autowired
	private ITabAiHistoryService tabAiHistoryService;
	/**
	 * 分页列表查询
	 *
	 * @param tabAiHistory
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "AI识别结果历史-分页列表查询")
	@ApiOperation(value="AI识别结果历史-分页列表查询", notes="AI识别结果历史-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TabAiHistory>> queryPageList(TabAiHistory tabAiHistory,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<TabAiHistory> queryWrapper = QueryGenerator.initQueryWrapper(tabAiHistory, req.getParameterMap());
		Page<TabAiHistory> page = new Page<TabAiHistory>(pageNo, pageSize);
		IPage<TabAiHistory> pageList = tabAiHistoryService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tabAiHistory
	 * @return
	 */
	@AutoLog(value = "AI识别结果历史-添加")
	@ApiOperation(value="AI识别结果历史-添加", notes="AI识别结果历史-添加")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_history:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TabAiHistory tabAiHistory) {
		tabAiHistoryService.save(tabAiHistory);
		return Result.OK("添加成功！");
	}
	 @AutoLog(value = "AI识别结果历史-添加")
	@ApiOperation(value="AI识别结果历史-添加", notes="旧执行入口已停用")
	@PostMapping(value = "/addIdentify")
	public Result<String> addIdentify(@RequestBody TabAiModelBund tabAiModelBund) {
		LegacyExecutionGuard.reject();
		return Result.error("旧 AI 执行入口已停用");
	}


	 @AutoLog(value = "AI语音结果识别结果历史-添加")
	@ApiOperation(value="AI语音结果识别结果历史-添加", notes="旧执行入口已停用")
	@GetMapping(value = "/addAudio")
	public Result<?> addAudio(@RequestParam(name="path",required=true) String path) {
		LegacyExecutionGuard.reject();
		return Result.error("旧 AI 执行入口已停用");
	}
	 @AutoLog(value = "AI识别结果历史-关闭")
	@ApiOperation(value="AI识别结果历史-关闭", notes="旧关闭入口不能代表远程停止")
	@PostMapping(value = "/addIdentifyClose")
	public Result<String> addIdentifyClose(@RequestBody TabAiModelBund tabAiModelBund) {
		LegacyExecutionGuard.reject();
		return Result.error("旧 AI 执行入口已停用");
	}
	/**
	 *  编辑
	 *
	 * @param tabAiHistory
	 * @return
	 */
	@AutoLog(value = "AI识别结果历史-编辑")
	@ApiOperation(value="AI识别结果历史-编辑", notes="AI识别结果历史-编辑")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_history:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TabAiHistory tabAiHistory) {
		tabAiHistoryService.updateById(tabAiHistory);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "AI识别结果历史-通过id删除")
	@ApiOperation(value="AI识别结果历史-通过id删除", notes="AI识别结果历史-通过id删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_history:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tabAiHistoryService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "AI识别结果历史-批量删除")
	@ApiOperation(value="AI识别结果历史-批量删除", notes="AI识别结果历史-批量删除")
	//@RequiresPermissions("org.jeecg.modules.demo:tab_ai_history:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tabAiHistoryService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "AI识别结果历史-通过id查询")
	@ApiOperation(value="AI识别结果历史-通过id查询", notes="AI识别结果历史-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TabAiHistory> queryById(@RequestParam(name="id",required=true) String id) {
		TabAiHistory tabAiHistory = tabAiHistoryService.getById(id);
		if(tabAiHistory==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tabAiHistory);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tabAiHistory
    */
    //@RequiresPermissions("org.jeecg.modules.demo:tab_ai_history:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TabAiHistory tabAiHistory) {
        return super.exportXls(request, tabAiHistory, TabAiHistory.class, "AI识别结果历史");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    //@RequiresPermissions("tab_ai_history:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TabAiHistory.class);
    }

}
