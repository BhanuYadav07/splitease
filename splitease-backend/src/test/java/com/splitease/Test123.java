package com.splitease;

import com.splitease.user.User;
import com.splitease.user.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mysql")
public class Test123 {
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(Test123.class.getName());
    @Test
    @Disabled
    public void tes(){
        boolean check =true;

        for(User user: userRepository.findAll()){
            System.out.println(user);
            logger.info("This is working {}",getClass());
            check=false;
        }
        if(check){
            logger.debug("UserRepo is Empty");
            System.out.println("Yes this is not written any list ");
        }
        User user1 = new User("Tarun","tarun@1234",passwordEncoder.encode("tar123456"));
        User user2 = new User("abhineet","abhi@1234",passwordEncoder.encode("abi123456"));
        User user3 = new User("anurag","anu@1234",passwordEncoder.encode("anu123456"));
        User user4 = new User("aksh","aksh@1234",passwordEncoder.encode("var123456"));
        User user5 = new User("varun","varun@1234",passwordEncoder.encode("var123456"));
        User user6 = new User("vatsal","vat@1234",passwordEncoder.encode("vat123456"));
        userRepository.save(user1); userRepository.save(user3); userRepository.save(user5);
        userRepository.save(user2); userRepository.save(user4); userRepository.save(user6);
        logger.info("Save into userRepo");
        check=true;
        for(User user : userRepository.findAll()){
            System.out.println(user);
            check=false;
        }
        if(check){
            logger.error("there is an issue in user Repo");
        }

    }
    @Test
    void Test1234(){
        for(User user:userRepository.findAllByOrderByName()){
            logger.info("user : {}",user);
        }
    }
    @Test
    void testmv(){
        for(User user:userRepository.findAll()){
            System.out.println(user);
        }
    }
}
