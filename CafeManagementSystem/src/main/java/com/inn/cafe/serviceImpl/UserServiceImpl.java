package com.inn.cafe.serviceImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.inn.cafe.Jwt.CustomerUsersDetailsService;
import com.inn.cafe.Jwt.JwtFilter;
import com.inn.cafe.Jwt.JwtUtil;
import com.inn.cafe.POJO.User;
import com.inn.cafe.constents.CafeConstants;
import com.inn.cafe.dao.UserDao;
import com.inn.cafe.service.UserService;
import com.inn.cafe.utils.CafeUtils;
import com.inn.cafe.utils.EmailUtils;
import com.inn.cafe.wrapper.UserWrapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserDao userDao;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    CustomerUsersDetailsService customerUserDetailsService;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JwtFilter jwtFilter;

    @Autowired
    EmailUtils emailUtils;

    @Override
    public ResponseEntity<String> signUp(Map<String, String> requestMap) {
        log.info("Inside signup{}", requestMap);
        try {
            if (validateSignupMap(requestMap)) {
                User user = userDao.findByEmailId(requestMap.get("email"));
                if (Objects.isNull(user)) {
                    userDao.save(getUserFromMap(requestMap));
                    return CafeUtils.getResponseEntity("Successfully Registered.", HttpStatus.OK);
                } else {
                    return CafeUtils.getResponseEntity("Email already exists.", HttpStatus.BAD_REQUEST);
                }
            } else {
                return CafeUtils.getResponseEntity(CafeConstants.INVALID_DATA, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean validateSignupMap(Map<String, String> requestMap) {
        return requestMap.containsKey("name") &&
               requestMap.containsKey("contactNumber") &&
               requestMap.containsKey("email") &&
               requestMap.containsKey("password");
    }

    private User getUserFromMap(Map<String, String> requestMap) {
        User user = new User();
        user.setName(requestMap.get("name"));
        user.setContactNumber(requestMap.get("contactNumber"));
        user.setEmail(requestMap.get("email"));
        user.setPassword(requestMap.get("password"));
        user.setStatus("false");
        user.setRole("user");
        return user;
    }

    @Override
    public ResponseEntity<String> login(Map<String, String> requestMap) {
        log.info("Inside login");
        try {
            org.springframework.security.core.Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requestMap.get("email"), requestMap.get("password"))
            );
            if (auth.isAuthenticated()) {
                if (customerUserDetailsService.getUserDetail().getStatus().equalsIgnoreCase("true")) {
                    return new ResponseEntity<>("{\"token\":\"" + jwtUtil.generateToken(customerUserDetailsService.getUserDetail().getEmail(), customerUserDetailsService.getUserDetail().getRole()) + "\"}", HttpStatus.OK);
                } else {
                    return new ResponseEntity<>("{\"message\":\"" + "Wait for admin approval." + "\"}", HttpStatus.BAD_REQUEST);
                }
            }
        } catch (Exception ex) {
            log.error("{}", ex);
        }
        return new ResponseEntity<>("{\"message\":\"" + "Bad Credentials." + "\"}", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<List<UserWrapper>> getAllUser() {
        try {
            if (jwtFilter.isAdmin()) {
                return new ResponseEntity<>(userDao.getAllUser(), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> update(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isUser()) {
                java.util.Optional<User> optional = userDao.findById(Integer.parseInt(requestMap.get("id")));
                if (optional.isPresent()) {
                    userDao.updateStatus(requestMap.get("status"), Integer.parseInt(requestMap.get("id")));
                    sendMailToAllAdmin(requestMap.get("status"), optional.get().getEmail(), userDao.getAllAdmin());
                    return CafeUtils.getResponseEntity("User status updated successfully", HttpStatus.OK);
                } else {
                    return CafeUtils.getResponseEntity("User ID does not exist", HttpStatus.OK);
                }
            } else {
                return CafeUtils.getResponseEntity(CafeConstants.UNAUTHORIZED_ACESS, HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void sendMailToAllAdmin(String status, String user, List<String> allAdmin) {
        allAdmin.remove(jwtFilter.getCurrentUser());
        if (status != null && status.equalsIgnoreCase("true")) {
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), "Account Approved", "USER:-" + user + "\n is approved by \nADMIN:-" + jwtFilter.getCurrentUser(), allAdmin);
        } else {
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), "Account Disabled", "USER:-" + user + "\n is approved by \nADMIN:-" + jwtFilter.getCurrentUser(), allAdmin);
        }
    }

    @Override
    public ResponseEntity<String> checkToken() {
        return CafeUtils.getResponseEntity("true", HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> changePassword(Map<String, String> requestMap) {
        try {
            User userObj = userDao.findByEmail(jwtFilter.getCurrentUser());
            if (userObj != null) {
                if (userObj.getPassword().equals(requestMap.get("oldPassword"))) {
                    userObj.setPassword(requestMap.get("newPassword"));
                    userDao.save(userObj);
                    return CafeUtils.getResponseEntity("Password updated successfully", HttpStatus.OK);
                }
                return CafeUtils.getResponseEntity("Incorrect old password", HttpStatus.BAD_REQUEST);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> forgotPassword(Map<String, String> requestMap) {
        try {
            User user = userDao.findByEmail(requestMap.get("email"));
            if (!Objects.isNull(user) && !org.springframework.util.StringUtils.isEmpty(user.getEmail())) {
                emailUtils.forgotMail(user.getEmail(), "Credentials by Cafe Management System", user.getPassword());
                return CafeUtils.getResponseEntity("Check your mail for credentials.", HttpStatus.OK);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

	
}

//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects; 
//
//import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.stereotype.Service;
//
//import com.google.common.base.Optional;
//import com.inn.cafe.Jwt.CustomerUsersDetailsService;
//import com.inn.cafe.Jwt.JwtFilter;
//import com.inn.cafe.Jwt.JwtUtil;
//import com.inn.cafe.POJO.User;
//import com.inn.cafe.constents.CafeConstants;
//import com.inn.cafe.dao.UserDao;
//import com.inn.cafe.service.UserService;
//import com.inn.cafe.utils.CafeUtils;
//import com.inn.cafe.utils.EmailUtils;
//import com.inn.cafe.wrapper.UserWrapper;
//
//import io.jsonwebtoken.lang.Strings;
//import lombok.extern.slf4j.Slf4j;
//@Slf4j
//@Service
//public class UserServiceImpl implements UserService {
//
//	@Autowired
//	UserDao userDao; 
//	
//	@Autowired
//	AuthenticationManager authenticationManager;
//	
//	@Autowired
//	CustomerUsersDetailsService customerUserDetailsService;
//	
//	@Autowired
//	JwtUtil jwtUtil;
//	
//	@Autowired
//	JwtFilter jwtFilter;
//	
//	@Autowired
//	EmailUtils emailUtils;
//	
//	@Override
//	public ResponseEntity<String> signUp(Map<String, String> requestMap) {
//		
//		log.info("Inside signup{}",requestMap);
//		try {
//			
//		if(validateSignupMap(requestMap)) {
//			User user =userDao.findByEmailId(requestMap.get("email"));
//			if(Objects.isNull(user)) {
//				userDao.save(getUserFromMap(requestMap));
//				return CafeUtils.getResponseEntity("Succesfully Registered.",HttpStatus.OK);
//				}
//			else
//			{
//				return CafeUtils.getResponseEntity("Email already exist.",HttpStatus.BAD_REQUEST);
//			}
//		}
//		else {
//			return CafeUtils.getResponseEntity(CafeConstants.INVALID_DATA,HttpStatus.BAD_REQUEST);
//		}
//	
//	}catch (Exception ex) {
//		ex.printStackTrace();
//	}
//		return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
//	}
//private boolean validateSignupMap(Map<String,String>requestMap) {
//	if(requestMap.containsKey("name")&& requestMap.containsKey("contctNumber")
//	&& requestMap.containsKey("email")&& requestMap.containsKey("password"))
//	{
//		return true;
//	}
//	return false;
//	
//	
//}
//   private User getUserFromMap(Map<String,String> requestMap) {
//	   User user=new User();
//	   user.setName(requestMap.get("name"));
//	   user.setContactNumber(requestMap.get("contactNumber"));
//	   user.setEmail(requestMap.get("email"));
//	   user.setPassword(requestMap.get("password"));
//	   user.setStatus("false");
//	   user.setRole("user");
//	   return user;
//   }
//@Override
//public ResponseEntity<String> login(Map<String, String> requestMap) {
//	log.info("Inside login");
//	try {
//		org.springframework.security.core.Authentication auth=authenticationManager.authenticate(
//				new UsernamePasswordAuthenticationToken(requestMap.get("email"),requestMap.get("password"))
//				);
//		if(auth.isAuthenticated()) {
//			if(customerUserDetailsService.getUserDetail().getStatus().equalsIgnoreCase("true")) {
//				return new ResponseEntity<String>("{\"token\":\""+jwtUtil.generateToken(customerUserDetailsService.getUserDetail().getEmail(),customerUserDetailsService.getUserDetail().getRole())+"\"}",
//						HttpStatus.OK);						
//			}
//			else {
//				return new ResponseEntity<String>("{\"message\":\""+"wait for admin approval."+"\"}",
//						HttpStatus.BAD_REQUEST);
//			}
//		}
//				
//	}catch(Exception
//			ex) {
//		log.error("{}",ex);
//	}
//	return new ResponseEntity<String>("{\"message\":\""+"Bad Credentials."+"\"}",
//			HttpStatus.BAD_REQUEST);
//	
//}
//@Override
//public ResponseEntity<List<UserWrapper>> getAllUSer() {
//	try {
//		if(jwtFilter.isAdmin()) {
//			return new ResponseEntity<>(userDao.getAllUser(),HttpStatus.OK);
//			
//		}else {
//			return new ResponseEntity<>(new ArrayList<>(),HttpStatus.UNAUTHORIZED);
//		}
//		
//	}catch (Exception ex) {
//		ex.printStackTrace();
//	}
//	return new ResponseEntity<>(new ArrayList<>(),HttpStatus.INTERNAL_SERVER_ERROR);
//}
//@Override
//public ResponseEntity<String> update(Map<String, String> requestMap) {
//	try {
//		if (jwtFilter.isUser()) {
//			java.util.Optional<User> optional= userDao.findById(Integer.parseInt(requestMap.get("id")));
//			if(!optional.isEmpty()) {
//				userDao.updateStatus(requestMap.get("status"),Integer.parseInt(requestMap.get("id")));
//				sendMailToAllAdmin(requestMap.get("status"),optional.get().getEmail(),userDao.getAllAdmin());
//				return CafeUtils.getResponseEntity("User status Updated Successfully",HttpStatus.OK);
//			}
//			else {
//				return CafeUtils.getResponseEntity("User id does not exist",HttpStatus.OK);
//			}
//		}else {
//			return CafeUtils.getResponseEntity(CafeConstants.UNAUTHORIZED_ACESS,HttpStatus.UNAUTHORIZED);
//		}
//		
//	}catch(Exception ex) {
//	ex.printStackTrace();
//}return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
//
//}
//private void sendMailToAllAdmin(String string, String email, List<String> allAdmin) {
//	allAdmin.remove(jwtFilter.getCurrentUser());
//	if(status!=null&&status.equalsIgnoreCase("true")) {
//		emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(),"Account Approved","USER:-"+user+"\n is approved by \nADMIN:-" + jwtFilter.getCurrentUser(),allAdmin);
//	}else {
//		emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(),"Account Disabled","USER:-"+user+"\n is approved by \nADMIN:-" + jwtFilter.getCurrentUser(),allAdmin);
//	}
//}
//@Override
//public ResponseEntity<String> checkToken() {
//	
//	return CafeUtils.getResponseEntity("true",HttpStatus.OK);
//}
//@Override
//public ResponseEntity<String> changePassword(Map<String, String> requestMap) {
//	try {
//		User userObj=userDao.findByEmail(jwtFilter.getCurrentUser());
//		if(!userObj.equals(null)) {
//			if(userObj.getPassword().equals(requestMap.get("oldPassword"))) {
//				userObj.setPassword(requestMap.get("newPassword"));
//				userDao.save(userObj);
//				return CafeUtils.getResponseEntity("Password Updated Succesfully",HttpStatus.OK);
//			}
//return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
//		}
//	return CafeUtils.getResponseEntity("Incorrect old password", HttpStatus.BAD_REQUEST);	
//	}catch(Exception ex) {
//		ex.printStackTrace();
//	}
//	return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
//}
//@Override
//public ResponseEntity<String> forgotPassword(Map<String, String> requestMap) {
//	try {
//		User user=userDao.findByEmail(requestMap.get("email"));
//		if(!Objects.isNull(user) && !Strings.isNullrEmpty(user.getEmail())) 
//			emailUtils.forgotMail(user.getEmail(),"Credentials by Cafe Management System",user.getPassword());
//		return CafeUtils.getResponseEntity("check your mail for Credentials.",HttpStatus.OK );
//	}catch(Exception ex) {
//		ex.printStackTrace();	
//	}
//	return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
//	
//}
// 
//
//
//
//
//}
// 