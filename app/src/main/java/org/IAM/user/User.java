package org.IAM.user;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Table(name = "users")
@Entity
public class User {
	@Id 
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID userID;

	@Column(name = "email", nullable = false, unique = true, updatable = true, length = 255)
	private String email;

	@Column(name = "password", nullable = false, updatable = true)
	private String password;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, updatable = true)
	private Instant updatedAt;

	@Column(name = "is_active", nullable = false, updatable = true)
	private Boolean isActive;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		this.isActive = true;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}
	protected User(){}
	public User(String email, String password){
		this.password = password;
		this.email = email;
	}

	public UUID getUserID() {
		return userID;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Boolean getIsActive() {
		return isActive;
	}
}

