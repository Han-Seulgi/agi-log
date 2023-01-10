package com.agilog.services;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpServletRequest;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.agilog.beans.AuthBean;
import com.agilog.beans.CompanyBean;
import com.agilog.interfaces.ServiceRule;
import com.agilog.utils.Encryption;
import com.agilog.utils.ProjectUtils;

@Service
public class Authentication implements ServiceRule {
	@Autowired
	private SqlSessionTemplate session;
	@Autowired
	private Encryption enc;
	@Autowired
	private ProjectUtils pu;
	private JavaMailSenderImpl mail;
	@Autowired
	private DashBoard dashBoard;

	public Authentication() {
	}

	public void backController(ModelAndView mav, int serviceCode) {
		switch (serviceCode) {
		case 1:
			this.moveLoginCtl(mav);
			break;
		case 2:
			this.moveJoinCtl(mav);
			break;
		case 3:
			this.moveCompanyPageCtl(mav);
			break;
		case 10:
			this.moveJoinFormPageCtl(mav);
			break;
		case 13:
			this.moveCompanyJoinPageCtl(mav);
			break;
		case 14:
			this.naverLoginCtl(mav);
			break;
		case 15:
			this.kakaoLoginCtl(mav);
			break;
		case 20:
			this.socialJoin(mav);
			break;
		case 21:
			this.companyJoinCtl(mav);
			break;
		case 29:
			this.companyLogin(mav);
			break;
		case 93:
			this.logoutCtl(mav);
			break;
		case 94:
			this.companyLogout(mav);
		}
	}

	public void backController(Model model, int serviceCode) {
		switch (serviceCode) {
		case 18:
			this.checkPersonalOverlap(model);
			break;
		}
	}

	private void checkPersonalOverlap(Model model) {
		String code = (String) model.getAttribute("code");
		AuthBean ab = (AuthBean) model.getAttribute("authBean");
		int result = 0;
		switch (code) {
		case "0":
			;
			break;
		case "1":
			result = Integer.parseInt(this.session.selectOne("checkNickName", ab).toString());
			break;
		}

		if (this.convertToBoolean(result)) {
			model.addAttribute("check", "중복된 닉네임");
		} else {
			model.addAttribute("check", "ok");
		}
	}

	//회원가입
	@Transactional(rollbackFor = SQLException.class)
	private void socialJoin(ModelAndView mav) {
		AuthBean ab = (AuthBean) mav.getModel().get("authBean");
		boolean type = false;

		if (ab.getSuCode().length() == 10) {
			type = true;
		}

		try {
			if(ab.getSuCode().length()>10)
				ab.setSuName(this.enc.aesEncode(ab.getSuName(), ab.getSuCode().substring(0, 10)));
			else 
				ab.setSuName(this.enc.aesEncode(ab.getSuName(), ab.getSuCode()));
			
			if(ab.getSuCode().length()>10)
				ab.setSuEmail(this.enc.aesEncode(ab.getSuEmail(), ab.getSuCode().substring(0, 10)));
			else 
				ab.setSuEmail(this.enc.aesEncode(ab.getSuEmail(), ab.getSuCode()));
			
			if(ab.getSuCode().length()>10)
				ab.setSuPhone(this.enc.aesEncode(ab.getSuPhone(), ab.getSuCode().substring(0, 10)));
			else 
				ab.setSuPhone(this.enc.aesEncode(ab.getSuPhone(), ab.getSuCode()));
			
			/*if(ab.getSuCode().length()>10)
				ab.setSuAddress(this.enc.aesEncode(ab.getSuAddress(), ab.getSuCode().substring(0, 10)));
			else 
				ab.setSuAddress(this.enc.aesEncode(ab.getSuAddress(), ab.getSuCode()));*/
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (this.convertToBoolean(this.session.insert("insSocialUser", ab))) {
			HttpServletRequest req = (HttpServletRequest)mav.getModel().get("req");

			/* 회원가입 성공시 유저별로 /res/img/유저코드로 폴더 생성*/
			String path = req.getSession().getServletContext().getRealPath("/resources/img/")+ab.getSuCode();
			File uploadPath = new File(path);
			if (!uploadPath.exists()) uploadPath.mkdirs();
		
			uploadPath = new File(path+"\\profile");
			if (!uploadPath.exists()) uploadPath.mkdirs();
			
			uploadPath = new File(path+"\\board");
			if (!uploadPath.exists()) uploadPath.mkdirs();
			
			uploadPath = new File(path+"\\dailydiary");
			if (!uploadPath.exists()) uploadPath.mkdirs();
			
			if (type) {
				this.kakaoLoginCtl(mav);
			} else {
				this.naverLoginCtl(mav);
			}
		}
		mav.addObject("message","회원가입성공");
	}

	// 기업회원 로그아웃 제어
	@Transactional(rollbackFor = SQLException.class)
	private void companyLogout(ModelAndView mav) {
		CompanyBean cb;
		try {
			cb = ((CompanyBean) this.pu.getAttribute("companyAccessInfo"));
			if (cb != null) {
				String accessState = this.session.selectOne("isCompanyAccess", cb);
				if (accessState != null) {
					cb.setClAction(-1);
					if (!this.convertToBoolean(this.session.insert("insCompanyAuthLog", cb))) {
					}
					this.pu.removeAttribute("companyAccessInfo");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		mav.setViewName("dashBoard");
	}

	// 추가해주기~~~~
	@Transactional(rollbackFor = SQLException.class)
	private void companyLogin(ModelAndView mav) {
		CompanyBean cb = ((CompanyBean) mav.getModel().get("companyBean"));
		
		//아이디로 패스워드 가져옴
		CompanyBean companyPw = this.session.selectOne("isCompanyMember", cb);

		// 암호 일치 확인
		  if (this.enc.matches(cb.getCoPassword(), companyPw.getCoPassword())) {
			// 일치함
			// 로그인상태인지 로그아웃상태인지 확인
			String accessState = this.session.selectOne("isCompanyAccess", cb);
			
		    if (accessState.equals("1")) {
				// 로그아웃 안된 상태, 로그아웃 AccessLog찍어주기
				cb.setClAction(-1);
				this.session.insert("insCompanyAuthLog", cb);
			}

			// AccessLog insert
			cb.setClAction(1);

			if (this.convertToBoolean(this.session.insert("insCompanyAuthLog", cb))) {
				try {
					// 세션에 저장할 로그인 유저 정보 가져오기
					CompanyBean company = (CompanyBean) this.session.selectList("getCompanyAccessInfo", cb).get(0);
					company.setCoName(company.getCoName());
					// 세션에 userCode저장
					this.pu.setAttribute("companyAccessInfo", company);
					// 병원이름 mav에 저장
					((CompanyBean) mav.getModel().get("companyBean")).setCoName(company.getCoName());

					mav.setViewName("checkManager");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			// 일치하지 않음
			mav.setViewName("companyLogin");
			mav.addObject("message", "로그인에 실패하였습니다");
		}
	}

	private void moveLoginCtl(ModelAndView mav) {
		try {
			String page = "";
			AuthBean ab = (AuthBean) this.pu.getAttribute("accessInfo");
			if (ab != null) {
				mav.addObject("accessInfo", ab);
				this.dashBoard.backController(mav, 4);
			} else {
				page = "login";
			}
			mav.setViewName(page);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void moveJoinCtl(ModelAndView mav) {
		try {
			String page = "";
			AuthBean ab = (AuthBean) this.pu.getAttribute("accessInfo");
			if (ab != null) {
				mav.addObject("accessInfo", ab);
				this.dashBoard.backController(mav, 4);
			} else {
				page = "login";
			}
			mav.setViewName("socialJoin");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void moveCompanyPageCtl(ModelAndView mav) {
		mav.setViewName("companyLogin");
	}
	//회원가입 정보입력 페이지 이동
	private void moveJoinFormPageCtl(ModelAndView mav) {
		AuthBean ab = (AuthBean) mav.getModel().get("authBean");
		String page = "login";
		if (ab.getSuCode() != null) {
			if (this.convertToBoolean(this.session.selectOne("isMember", ab))) {
				mav.addObject("message", "이미 가입된 회원입니다.");
				page = "login";
			} else {
				page = "joinForm";
			}

			mav.addObject("socialInfo", ab);
		}
		mav.setViewName(page);
	}

	private void naverJoinCtl(ModelAndView mav) {

	}

	private void insertNaverMemberCtl(ModelAndView mav) {

	}

	private void kakaoJoinCtl(ModelAndView mav) {

	}

	private void insertKakaoMemberCtl(ModelAndView mav) {

	}

	// 기업 회원가입 페이지 이동
	private void moveCompanyJoinPageCtl(ModelAndView mav) {
		mav.setViewName("companyJoin");
	}
	
	//네이버 로그인
	@Transactional(rollbackFor = SQLException.class)
	private void naverLoginCtl(ModelAndView mav) {
		try {
			String page = "login", action = "";
			if (this.pu.getAttribute("accessInfo") != null) {
				this.dashBoard.backController(mav, 4);
			} else {
				AuthBean ab = (AuthBean) mav.getModel().get("authBean");
				if (this.convertToBoolean(this.session.selectOne("isMember", ab))) {
					action = this.session.selectOne("isAccess", ab);
					if (action != null && Integer.parseInt(action) > 0) {
						ab.setAlAction(-1);
						this.session.insert("insAuthLog", ab);
					}
					ab.setAlAction(1);
					if (this.convertToBoolean(this.session.insert("insAuthLog", ab))) {
						AuthBean a = (AuthBean) this.session.selectOne("getAccessInfo", ab);
						if (a != null) {
							if (a.getSuCode().length() == 10) {
								a.setType("kakao");
							} else {
								a.setType("naver");
							}
							this.pu.setAttribute("accessInfo", a);
							mav.addObject("accessInfo", a);
							this.dashBoard.backController(mav, 4);
						}
					}
				} else {
					mav.addObject("message", "가입된 회원이 아닙니다.");
					mav.setViewName(page);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//카카오 로그인
	@Transactional(rollbackFor = SQLException.class)
	private void kakaoLoginCtl(ModelAndView mav) {
		try {
			String page = "login";
			if (this.pu.getAttribute("accessInfo") != null) {
				this.dashBoard.backController(mav, 4);
			} else {
				AuthBean ab = (AuthBean) mav.getModel().get("authBean");
				if (this.convertToBoolean(this.session.selectOne("isMember", ab))) {
					String action = this.session.selectOne("isAccess", ab);
					if (action != null && Integer.parseInt(action) > 0) {
						ab.setAlAction(-1);
						this.session.insert("insAuthLog", ab);
					}
					ab.setAlAction(1);
					if (this.convertToBoolean(this.session.insert("insAuthLog", ab))) {
						AuthBean a = (AuthBean) this.session.selectOne("getAccessInfo", ab);
						if (a != null) {
							if (a.getSuCode().length() == 10) {
								a.setType("kakao");
							} else {
								a.setType("naver");
							}
							this.pu.setAttribute("accessInfo", a);
							mav.addObject("accessInfo", a);
							this.dashBoard.backController(mav, 4);
						}
					}
				} else {
					mav.addObject("message", "가입된 회원이 아닙니다.");
					mav.setViewName(page);
				}
			}
//			mav.setViewName(page);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void moveFindCompanyPwCtl(ModelAndView mav) {

	}

	private void findIdCtl(ModelAndView mav) {

	}

	private void sendEmail(AuthBean ab, String title, String content) {

	}

	private void findPersonalPwCtl(ModelAndView mav) {

	}

	private void findCompanyPwCtl(ModelAndView mav) {

	}

	private void moveFindPwPageCtl(ModelAndView mav) {

	}

	// 기업 회원가입
	@Transactional(rollbackFor = SQLException.class)
	private void companyJoinCtl(ModelAndView mav) {
		CompanyBean cb = (CompanyBean) mav.getModel().get("companyBean");
		MultipartFile file = (MultipartFile)mav.getModel().get("file");
		HttpServletRequest req = (HttpServletRequest)mav.getModel().get("req");
		
		try {
			// 섀도우 방식 암호화 : 비밀번호, 메일
			cb.setCoPassword(this.enc.encode(cb.getCoPassword()));
			cb.setCoEmail(this.enc.encode(cb.getCoEmail()));

			// aes 방식 암호화 : 이름, 연락처, 주소, 관리자코드 -> 키값은 사업자등록번호
//			cb.setCoName(this.enc.aesEncode(cb.getCoName(), cb.getCoCode()));
//			cb.setCoPhone(this.enc.aesEncode(cb.getCoPhone(), cb.getCoCode()));
//			cb.setCoAddress(this.enc.aesEncode(cb.getCoAddress(), cb.getCoCode()));
			cb.setCoManagerCode(this.enc.aesEncode(cb.getCoManagerCode(), cb.getCoCode()));
			
			if(file!=null) {
				/* 확장자 뽑아내서 파일이름 만들어주기 */
				int pos = file.getOriginalFilename().lastIndexOf(".");
				String ext = file.getOriginalFilename().substring(pos);
				String fileName = cb.getCoCode()+ext; //유저코드 + 확장자
				
				String suPhoto = "/res/img/"+cb.getCoCode()+"/"+fileName;
				cb.setCoPhoto(suPhoto);
			}else {
				String suPhoto = "/res/img/profile_default.png";
				cb.setCoPhoto(suPhoto);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (this.convertToBoolean(this.session.insert("insCompanyInfo", cb))) {
			if(file !=null) {
				/* 회원가입 성공시 기업별로 /res/img/기업코드로 폴더 생성*/
				String path = req.getSession().getServletContext().getRealPath("/resources/img/")+cb.getCoCode();
				File uploadPath = new File(path);
				if (!uploadPath.exists()) uploadPath.mkdirs();
				
				/* 확장자 뽑아내서 파일이름 만들어주기 */
				int pos = file.getOriginalFilename().lastIndexOf(".");
				String ext = file.getOriginalFilename().substring(pos);
				String fileName = cb.getCoCode()+ext; //유저코드 + 확장자
				
				/* 기업 프로필이미지 경로에 집어넣기 */
				File realPath = new File(path,fileName);
				try {
					file.transferTo(realPath);
				} catch (IllegalStateException | IOException e) {
					e.printStackTrace();
				}
				
			}
			mav.setViewName("companyLogin");
		} else {
		}
		mav.setViewName("companyLogin");
	}

	private void checkCompanyCodeCtl(ModelAndView mav) {

	}

	private void checkCompanyAddressCtl(ModelAndView mav) {

	}

	private void companyLoginCtl(ModelAndView mav) {

	}

	@Transactional
	private void logoutCtl(ModelAndView mav) {
		try {
			AuthBean ab = (AuthBean) this.pu.getAttribute("accessInfo");
			if (ab != null) {
				if (this.session.selectOne("isAccess", ab) != null) {
					ab.setAlAction(-1);
					if (this.convertToBoolean(this.session.insert("insAuthLog", ab))) {
						this.pu.removeAttribute("accessInfo");
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.dashBoard.backController(mav, 4);
	}

	private void insertDoctorCtl(ModelAndView mav) {

	}

	private void checkDoctorCtl(ModelAndView mav) {

	}

	private void personalLoginCtl(ModelAndView mav) {

	}

	private String makeRandomPw(String random) {
		String randomPw = null;

		return randomPw;
	}

	private boolean convertToBoolean(int booleanCheck) {
		boolean result = false;
		result = booleanCheck == 0 ? false : true;
		return result;
	}
}
