package com.praisesystem.backend.users.impl;

import com.praisesystem.backend.common.exceptions.exceptiontypes.NotFoundObjectException;
import com.praisesystem.backend.configuration.properties.ApplicationProperties;
import com.praisesystem.backend.users.UserRepository;
import com.praisesystem.backend.users.dto.UserDto;
import com.praisesystem.backend.users.mapper.UserMapper;
import com.praisesystem.backend.users.model.UserEntity;
import com.praisesystem.backend.users.roles.RoleService;
import com.praisesystem.backend.users.roles.enums.RoleCode;
import com.praisesystem.backend.users.roles.model.RoleEntity;
import com.praisesystem.backend.users.services.UserTransactionalService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j(topic = "[USER TRANSACTIONAL SERVICE]")
@Service
@Transactional
@AllArgsConstructor
public class UserTransactionalServiceImpl implements UserTransactionalService {

    ApplicationProperties properties;
    UserRepository userRepository;
    RoleService roleService;
    UserMapper userMapper;

    @Override
    public void createAdmins() {
        List<RoleEntity> roles = roleService.findAll();
        List<String> adminAddresses = properties.getAdminAddresses();

        List<String> existingAdmins = userRepository.findByEthereumAddressIn(adminAddresses)
                .stream()
                .map(UserEntity::getEthereumAddress)
                .collect(Collectors.toList());

        List<UserEntity> newAdmins = adminAddresses.stream()
                .distinct()
                .filter(address -> !existingAdmins.contains(address))
                .map(address -> userMapper.toNewUserFromEthereumAddressAndRoles(address, roles))
                .collect(Collectors.toList());

        userRepository.saveAll(newAdmins)
                .forEach(user -> log.info("Admin with address ({}) successfully created.", user.getEthereumAddress()));
        ;
    }

    @Override
    public UserEntity register(String ethereumAddress) {
        RoleEntity roleUser = roleService.findByCode(RoleCode.ROLE_USER);
        UserEntity newUser = userMapper.toNewUserFromEthereumAddressAndRoles(ethereumAddress, Collections.singletonList(roleUser));
        return userRepository.save(newUser);
    }

    @Override
    public UserDto findById(Long id) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new NotFoundObjectException("User not found"));
        return userMapper.toUserDto(user);
    }

    @Override
    public UserDto findByEthereumAddress(String ethereumAddress) {
        UserEntity user = userRepository.findByEthereumAddress(ethereumAddress).orElseGet(() -> register(ethereumAddress));
        return userMapper.toUserDto(user);
    }

    @Override
    public void updateNonceByEthereumAddress(String ethereumAddress) {
        UserEntity user = userRepository.findByEthereumAddress(ethereumAddress).orElseGet(() -> register(ethereumAddress));
        user.updateNonce();
        userRepository.save(user);
    }

    @Override
    public Long countUsers() {
        return userRepository.count();
    }

    @Override
    public Set<UserEntity> findRandomUsers(Long requiredCount) {
        Set<Long> quantifiersIds = new HashSet<>();

        List<Long> idsInRepo = userRepository.getAllIds();
        int totalIds = idsInRepo.size();

        while (quantifiersIds.size() < requiredCount) {
            quantifiersIds.add(idsInRepo.get((int) ThreadLocalRandom.current().nextLong(totalIds)));
        }
        return userRepository.findUserEntitiesByIdIn(quantifiersIds);
    }

    @Override
    public List<UserDto> findAll() {
        return userRepository.findAll().stream().map(userMapper::toUserDto).collect(Collectors.toList());
    }
}
