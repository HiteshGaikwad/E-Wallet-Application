package com.project.wallet;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class WallertService {

    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    ObjectMapper objectMapper;

    @KafkaListener(topics = "create_wallet", groupId = "test1234")
    public void createWallet(String massage){

        Wallet wallet= Wallet.builder().userName(massage).balance(100).build();

        walletRepository.save(wallet);
    }


    @KafkaListener(topics="update_wallet",groupId="test1234")
    public void updateWallet(String message) throws JsonProcessingException {

        //decoded back to JSonObject and extract information
        JSONObject jsonObject = objectMapper.readValue(message,JSONObject.class);

        String fromUser = (String)jsonObject.get("fromUser");
        String toUser = (String)jsonObject.get("toUser");
        int transactionAmount = (Integer)jsonObject.get("amount");
        String transactionId = (String)jsonObject.get("transactionId");

        System.out.println(fromUser+" -- "+toUser+"-- "+transactionAmount+" -- "+transactionId);

        JSONObject returnObject = new JSONObject();

        returnObject.put("transactionId",transactionId);

        Wallet fromUserWallet = walletRepository.findWalletByUserName(fromUser);

        Wallet receiverWallet = walletRepository.findWalletByUserName(toUser);

        if(fromUserWallet.getBalance()>=transactionAmount){

            //That is a successful transaction
            returnObject.put("status","SUCCESS");

            kafkaTemplate.send("update_transaction",objectMapper.writeValueAsString(returnObject));


            //Update the sender and receiver's wallet

            fromUserWallet.setBalance(fromUserWallet.getBalance() - transactionAmount);
            walletRepository.save(fromUserWallet);

            receiverWallet.setBalance(receiverWallet.getBalance() + transactionAmount);
            walletRepository.save(receiverWallet);


        }else{

            //INSUFFICIENT BALANCE --->
            returnObject.put("status","FAILED");
            kafkaTemplate.send("update_transaction",objectMapper.writeValueAsString(returnObject));

            //Not update the wallets.
        }


    }


}
