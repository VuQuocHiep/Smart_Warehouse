package com.example.iot.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.example.iot.dto.request.UserRequest;
import com.example.iot.entity.UserEntity;
import com.example.iot.mapper.UserMapper;
import com.example.iot.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    public UserEntity create(UserRequest request){
        if(userRepository.existsByEmailAndDeletedFalse(request.getEmail())){
            throw new DuplicateKeyException("Đã tồn tại!");
        }
        UserEntity userEntity = userMapper.toEntity(request);
        return userRepository.save(userEntity);
    }
    public UserEntity update(UserRequest request,String id){
        if(!userRepository.existsByUserIdAndDeletedFalse(id)){
            throw new RuntimeException("Không tồn tại!");
        }
        UserEntity userEntity = userMapper.toEntity(request);
        return userRepository.save(userEntity);
    }
    public void delete(String id){
        UserEntity userEntity = userRepository.findByUserIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        userEntity.setDeleted(true);
        userRepository.save(userEntity);
    }
    public List<UserEntity> getAll(){
        return userRepository.findByDeletedFalse();
    }
    public UserEntity getUserById(String id){
        return userRepository.findByUserIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }
}
