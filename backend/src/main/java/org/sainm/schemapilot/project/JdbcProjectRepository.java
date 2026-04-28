package org.sainm.schemapilot.project;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcProjectRepository implements ProjectRepository {
    private final JdbcClient jdbcClient;

    JdbcProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Project save(Project project) {
        jdbcClient.sql("""
                        insert into project (id, name, description, status, created_at, updated_at)
                        values (:id, :name, :description, :status, :createdAt, :updatedAt)
                        """)
                .param("id", project.id())
                .param("name", project.name())
                .param("description", project.description())
                .param("status", project.status().name())
                .param("createdAt", project.createdAt())
                .param("updatedAt", project.updatedAt())
                .update();
        return project;
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return jdbcClient.sql("""
                        select id, name, description, status, created_at, updated_at
                        from project
                        where id = :id
                        """)
                .param("id", id)
                .query(this::mapProject)
                .optional();
    }

    @Override
    public List<Project> findAll() {
        return jdbcClient.sql("""
                        select id, name, description, status, created_at, updated_at
                        from project
                        order by updated_at desc
                        """)
                .query(this::mapProject)
                .list();
    }

    @Override
    public Project update(Project project) {
        jdbcClient.sql("""
                        update project
                        set name = :name,
                            description = :description,
                            status = :status,
                            updated_at = :updatedAt
                        where id = :id
                        """)
                .param("id", project.id())
                .param("name", project.name())
                .param("description", project.description())
                .param("status", project.status().name())
                .param("updatedAt", project.updatedAt())
                .update();
        return project;
    }

    private Project mapProject(ResultSet rs, int rowNum) throws SQLException {
        return new Project(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                ProjectStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
