package org.jeecg.modules.demo.tab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.demo.tab.entity.TabAiHistory;
import org.jeecg.modules.demo.tab.mapper.TabAiHistoryMapper;
import org.jeecg.modules.demo.tab.service.ITabAiHistoryService;
import org.springframework.stereotype.Service;

/** Keeps historical CRUD available without retaining any local inference implementation. */
@Service
public class TabAiHistoryServiceImpl
        extends ServiceImpl<TabAiHistoryMapper, TabAiHistory>
        implements ITabAiHistoryService {
}
