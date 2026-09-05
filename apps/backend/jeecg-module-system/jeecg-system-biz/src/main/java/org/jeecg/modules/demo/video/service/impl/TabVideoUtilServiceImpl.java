package org.jeecg.modules.demo.video.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.demo.video.entity.TabVideoUtil;
import org.jeecg.modules.demo.video.mapper.TabVideoUtilMapper;
import org.jeecg.modules.demo.video.service.ITabVideoUtilService;
import org.springframework.stereotype.Service;

/** Keeps region configuration rows readable without starting a local model or camera. */
@Service
public class TabVideoUtilServiceImpl
        extends ServiceImpl<TabVideoUtilMapper, TabVideoUtil>
        implements ITabVideoUtilService {
}
