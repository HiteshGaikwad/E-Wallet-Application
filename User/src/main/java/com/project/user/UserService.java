package com.project.user;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RedisTemplate<String,User> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;


    String addUser(UserRequestDto userRequest)
    {
        //
        User user = User.builder().userName(userRequest.getUserName()).age(userRequest.getAge()).mobile(userRequest.getMobile())
                .email(userRequest.getEmail()).name(userRequest.getName()).build();

        //Save it to the db
        userRepository.save(user);
        //Save it in the cache
        saveInCache(user);

        //Send an update to the wallet module/ wallet service ---> that create a new wallet from the userName sent as a string.
        kafkaTemplate.send("create_wallet",user.getUserName());

        callNotificationService(user);

        return "User Added successfully";

    }

    public void saveInCache(User user){

        Map map = objectMapper.convertValue(user,Map.class);

        String key = "USER_KEY"+user.getUserName();
        System.out.println("The user key is "+key);
        redisTemplate.opsForHash().putAll(key,map);
        redisTemplate.expire(key, Duration.ofHours(12));
    }

    public User findUserByUserName(String userName){

        // 1. find in the redis cache
        Map map= redisTemplate.opsForHash().entries(userName);

        User user=null;
        //if not found in redis/map
        if(map==null){

            //find the userObject from db
             user= userRepository.findByUserName(userName);

            //save that found user in the cache
            saveInCache(user);
            return user;
        }
        else{
            //we found out the user object
             user= objectMapper.convertValue(map, User.class);
            return user;
        }
    }

    public UserResponseDto findEmailAndNameDto(String userName){


        User user = userRepository.findByUserName(userName);

        UserResponseDto userResponseDto = UserResponseDto.builder().email(user.getEmail()).name(user.getName()).build();

        return userResponseDto;
    }
    public void callNotificationService(User user) {


        String userName = user.getUserName();

        int userId = user.getId();

        //SEND THE EMAIL AND MESSAGE TO NOTIFICATIONS-SERVICE VIA KAFKA

        JSONObject emailRequest = new JSONObject();

        System.out.println("We are in User Service Layer" + userName + " " + user.getEmail());

        //User should always receive email ---->

        emailRequest.put("email", user.getEmail());

        String SenderMessageBody = String.format("Hello %s \n" +
                        "Your Account has been created successfully with userId %s. ",


                userName, userId);

        emailRequest.put("message", SenderMessageBody);

        String message = emailRequest.toString();

        //SEND IT TO KAFKA
        kafkaTemplate.send("send_email", message);

    }

}
