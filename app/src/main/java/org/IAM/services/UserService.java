package org.IAM.services;

import org.IAM.repositories.user.User;
import org.IAM.repositories.user.UserRepository;
import org.springframework.stereotype.Service;

@Service 
public class UserService {
	private final UserRepository userRepository;
	public UserService(UserRepository userRepository) {
		this.userRepository  = userRepository;
	}
	public User saveUser(User user){
		return userRepository.save(user);
	}
	public User findById(Long id){
		return userRepository.findById(id).orElseThrow();
	}
}
