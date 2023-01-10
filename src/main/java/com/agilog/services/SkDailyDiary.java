package com.agilog.services;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

import com.agilog.beans.AuthBean;
import com.agilog.beans.DailyDiaryBean;
import com.agilog.interfaces.ServiceRule;
import com.agilog.utils.Encryption;
import com.agilog.utils.ProjectUtils;

@Service
public class SkDailyDiary implements ServiceRule {

	@Autowired
	private SqlSessionTemplate session;
	@Autowired
	private ProjectUtils pu;

	public SkDailyDiary() {
	}

	// Controller��
	public void backController(ModelAndView mav, int serviceCode) {
		switch (serviceCode) {

		}
	}

	public void backController(Model model, int serviceCode) {
		switch (serviceCode) {
		case 110:
			this.dailyDiaryLikeCtl(model);
			break;
		}
	}

	// 감성일기 좋아요
	@Transactional(rollbackFor = SQLException.class)
	private void dailyDiaryLikeCtl(Model model) {
		DailyDiaryBean ddb = (DailyDiaryBean) model.getAttribute("dailyDiaryBean");
		AuthBean ab;
		try {
			ab = (AuthBean) this.pu.getAttribute("accessInfo");
			if (ab != null) {
				ddb.setSuCode(ab.getSuCode());
				
				//날짜 형식 변환
				SimpleDateFormat form = new SimpleDateFormat("yyyyMMddHHmmss");
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
				
				Date d = form.parse(ddb.getDdDate());
				ddb.setDdDate(sdf.format(d));
				
				HashMap<String, Object> map = new HashMap<String, Object>();
				
				// 0개 일때 !false=>좋아요 누른적 없음 => 좋아요 등록
				if (!this.convertToBoolean(this.session.selectOne("isDdLike", ddb))) {
					if (this.convertToBoolean(this.session.insert("insDdLike", ddb))) {
						// 현재 유저의 좋아요 여부 저장
						ddb.setLike(true);
						// 해당일기 전체 좋아요 수 조회
						ddb.setLikes(this.session.selectOne("getDdLike", ddb));
						// 해당일기 좋아요 수 업데이트
						if (this.convertToBoolean(this.session.update("updDdLike", ddb))) {
							map.put("ddLike", ddb);
							model.addAttribute("ddLike", map);
						}
					}
				} else { // 1개 일때 !true=>좋아요 누른적 있음 => 좋아요 삭제
					if (this.convertToBoolean(this.session.delete("delMyDdLike", ddb))) {
						// 현재 유저의 좋아요 여부 저장
						ddb.setLike(false);
						// 해당일기 전체 좋아요 수 조회
						ddb.setLikes(this.session.selectOne("getDdLike", ddb));
						// 해당일기 좋아요 수 업데이트
						if (this.convertToBoolean(this.session.update("updDdLike", ddb))) {
							map.put("ddLike", ddb);
							model.addAttribute("ddLike", map);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// BooleanCheck ��
	private boolean convertToBoolean(int booleanCheck) {
		return booleanCheck == 0 ? false : true;

	}
}
