package anordine.dao.simplifier.webflux.example.users;

import anordine.dao.simplifier.webflux.entity.SoftDeleteUuidEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("app_user")
public class UserEntity extends SoftDeleteUuidEntity {

    @Column("email")
    private String email;

    @Column("display_name")
    private String displayName;

    @Column("role")
    private String role;

    @Column("status")
    private String status;

    @Column("city")
    private String city;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
