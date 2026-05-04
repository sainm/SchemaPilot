package org.sainm.schemapilot.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.DbObjectType;
import org.sainm.schemapilot.model.ObjectStatus;
import org.sainm.schemapilot.model.SourceLocation;

class DependencyGraphServiceTest {

    private final InMemoryDependencyRepository repository = new InMemoryDependencyRepository();
    private final DependencyGraphService service = new DependencyGraphService(repository);

    @Test
    void buildsViewTriggerRoutineAndCrossSourceDependencies() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID otherSourceProjectId = UUID.randomUUID();
        DbObject users = object(projectId, sourceProjectId, DbObjectType.TABLE, "users", "create table users (id number)");
        DbObject orders = object(projectId, otherSourceProjectId, DbObjectType.TABLE, "orders", "create table orders (id number)");
        DbObject view = object(projectId, sourceProjectId, DbObjectType.VIEW, "v_users", "create view v_users as select * from users");
        DbObject trigger = object(projectId, sourceProjectId, DbObjectType.TRIGGER, "users_bi", "create trigger users_bi before insert on users begin null; end;");
        DbObject routine = object(projectId, sourceProjectId, DbObjectType.PROCEDURE, "sync_orders", "create procedure sync_orders as begin insert into orders values (1); end;");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(users, orders, view, trigger, routine));

        assertThat(dependencies).extracting(ObjectDependency::type)
                .contains(
                        DependencyType.VIEW_REFERENCES_TABLE,
                        DependencyType.TRIGGER_ON_TABLE,
                        DependencyType.CROSS_SOURCE_DEPENDENCY);
        assertThat(repository.findDependencies(projectId)).hasSize(dependencies.size());
    }

    @Test
    void ignoresSubstringMatchesThatAreNotSqlIdentifiers() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject user = object(projectId, sourceProjectId, DbObjectType.TABLE, "user", "create table user (id number)");
        DbObject view = object(
                projectId,
                sourceProjectId,
                DbObjectType.VIEW,
                "v_superuser",
                "create view v_superuser as select 'user' as label, superuser_id from account");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(user, view));

        assertThat(dependencies).isEmpty();
    }

    @Test
    void ignoresObjectNamesInsideOracleQQuotedLiterals() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject users = object(projectId, sourceProjectId, DbObjectType.TABLE, "users", "create table users (id number)");
        DbObject view = object(
                projectId,
                sourceProjectId,
                DbObjectType.VIEW,
                "v_notes",
                "create view v_notes as select q'[Bob's users note]' as label from dual");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(users, view));

        assertThat(dependencies).isEmpty();
    }

    @Test
    void ignoresObjectNamesInViewDeclarationHeader() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID otherSourceProjectId = UUID.randomUUID();
        DbObject otherViewTable = object(projectId, otherSourceProjectId, DbObjectType.TABLE, "v_users", "create table v_users (id number)");
        DbObject view = object(
                projectId,
                sourceProjectId,
                DbObjectType.VIEW,
                "v_users",
                "create view v_users as select 1 as id from dual");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(otherViewTable, view));

        assertThat(dependencies).isEmpty();
    }

    @Test
    void prefersSameSourceObjectWhenDuplicateNamesExist() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID otherSourceProjectId = UUID.randomUUID();
        DbObject localUsers = object(projectId, sourceProjectId, DbObjectType.TABLE, "users", "create table users (id number)");
        DbObject otherUsers = object(projectId, otherSourceProjectId, DbObjectType.TABLE, "users", "create table users (id number)");
        DbObject view = object(projectId, sourceProjectId, DbObjectType.VIEW, "v_users", "create view v_users as select * from users");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(localUsers, otherUsers, view));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.targetObjectId()).isEqualTo(localUsers.id());
                    assertThat(dependency.type()).isEqualTo(DependencyType.VIEW_REFERENCES_TABLE);
                });
    }

    @Test
    void schemaQualifiedReferenceDoesNotMatchSameNamedObjectsInOtherSchemas() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject appUsers = object(projectId, sourceProjectId, DbObjectType.TABLE, "app", "users", "create table app.users (id number)");
        DbObject auditUsers = object(projectId, sourceProjectId, DbObjectType.TABLE, "audit", "users", "create table audit.users (id number)");
        DbObject view = object(projectId, sourceProjectId, DbObjectType.VIEW, "app", "v_users", "create view app.v_users as select * from app.users");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(appUsers, auditUsers, view));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> assertThat(dependency.targetObjectId()).isEqualTo(appUsers.id()));
    }

    @Test
    void keepsUnqualifiedReferenceWhenQualifiedReferenceWithSameNameExists() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject localUsers = object(projectId, sourceProjectId, DbObjectType.TABLE, "users", "create table users (id number)");
        DbObject appUsers = object(projectId, sourceProjectId, DbObjectType.TABLE, "app", "users", "create table app.users (id number)");
        DbObject view = object(
                projectId,
                sourceProjectId,
                DbObjectType.VIEW,
                "v_all_users",
                "create view v_all_users as select * from app.users union all select * from users");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(localUsers, appUsers, view));

        assertThat(dependencies).extracting(ObjectDependency::targetObjectId)
                .containsExactlyInAnyOrder(localUsers.id(), appUsers.id());
    }

    @Test
    void matchesQuotedSchemaQualifiedReferences() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject userAccount = object(projectId, sourceProjectId, DbObjectType.TABLE, "HR", "User Account", "create table HR.\"User Account\" (id number)");
        DbObject auditUserAccount = object(projectId, sourceProjectId, DbObjectType.TABLE, "AUDIT", "User Account", "create table AUDIT.\"User Account\" (id number)");
        DbObject view = object(projectId, sourceProjectId, DbObjectType.VIEW, "HR", "v_accounts", "create view HR.v_accounts as select * from \"HR\".\"User Account\"");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(userAccount, auditUserAccount, view));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> assertThat(dependency.targetObjectId()).isEqualTo(userAccount.id()));
    }

    @Test
    void triggerDependencyIgnoresCommentedOnClausesAndMatchesQuotedNames() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject auditLog = object(projectId, sourceProjectId, DbObjectType.TABLE, "audit_log", "create table audit_log (id number)");
        DbObject userAccount = object(projectId, sourceProjectId, DbObjectType.TABLE, "User Account", "create table \"User Account\" (id number)");
        DbObject trigger = object(
                projectId,
                sourceProjectId,
                DbObjectType.TRIGGER,
                "accounts_bi",
                """
                        create trigger accounts_bi
                        -- previous draft was on audit_log
                        before insert on "User Account"
                        begin null; end;
                        """);

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(auditLog, userAccount, trigger));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> assertThat(dependency.targetObjectId()).isEqualTo(userAccount.id()));
    }

    @Test
    void tableForeignKeysCreatePrerequisiteEdges() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject departments = object(projectId, sourceProjectId, DbObjectType.TABLE, "departments", "create table departments (id number)");
        DbObject employees = object(
                projectId,
                sourceProjectId,
                DbObjectType.TABLE,
                "employees",
                """
                        create table employees (
                          id number,
                          department_id number references departments(id),
                          constraint employees_department_fk foreign key (department_id) references departments(id)
                        )
                        """);

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(departments, employees));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.sourceObjectId()).isEqualTo(employees.id());
                    assertThat(dependency.targetObjectId()).isEqualTo(departments.id());
                    assertThat(dependency.type()).isEqualTo(DependencyType.TABLE_REFERENCES_TABLE);
                });
    }

    @Test
    void resolvesQuotedQualifiedTableReferences() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject departments = object(projectId, sourceProjectId, DbObjectType.TABLE, "HR", "Departments", "create table \"HR\".\"Departments\" (id number)");
        DbObject employees = object(
                projectId,
                sourceProjectId,
                DbObjectType.TABLE,
                "HR",
                "Employees",
                "create table \"HR\".\"Employees\" (department_id number references \"HR\".\"Departments\"(id))");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(departments, employees));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.sourceObjectId()).isEqualTo(employees.id());
                    assertThat(dependency.targetObjectId()).isEqualTo(departments.id());
                    assertThat(dependency.type()).isEqualTo(DependencyType.TABLE_REFERENCES_TABLE);
                });
    }

    @Test
    void resolvesQuotedQualifiedSequenceDefaults() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject sequence = object(projectId, sourceProjectId, DbObjectType.SEQUENCE, "HR", "UserSeq", "create sequence \"HR\".\"UserSeq\"");
        DbObject users = object(
                projectId,
                sourceProjectId,
                DbObjectType.TABLE,
                "HR",
                "Users",
                "create table \"HR\".\"Users\" (id number default \"HR\".\"UserSeq\".nextval primary key)");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(sequence, users));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.sourceObjectId()).isEqualTo(users.id());
                    assertThat(dependency.targetObjectId()).isEqualTo(sequence.id());
                    assertThat(dependency.type()).isEqualTo(DependencyType.TABLE_USES_SEQUENCE);
                });
    }

    @Test
    void indexCreatesTablePrerequisiteEdge() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject users = object(projectId, sourceProjectId, DbObjectType.TABLE, "users", "create table users (id number)");
        DbObject index = object(projectId, sourceProjectId, DbObjectType.INDEX, "ix_users_id", "create index ix_users_id on users(id)");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(users, index));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.sourceObjectId()).isEqualTo(index.id());
                    assertThat(dependency.targetObjectId()).isEqualTo(users.id());
                    assertThat(dependency.type().name()).isEqualTo("INDEX_ON_TABLE");
                });
    }

    @Test
    void sequenceDefaultsCreateTablePrerequisiteEdges() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject sequence = object(projectId, sourceProjectId, DbObjectType.SEQUENCE, "users_seq", "create sequence users_seq");
        DbObject users = object(
                projectId,
                sourceProjectId,
                DbObjectType.TABLE,
                "users",
                "create table users (id number default users_seq.nextval primary key)");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(sequence, users));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.sourceObjectId()).isEqualTo(users.id());
                    assertThat(dependency.targetObjectId()).isEqualTo(sequence.id());
                    assertThat(dependency.type().name()).isEqualTo("TABLE_USES_SEQUENCE");
                });
    }

    @Test
    void packageBodyPrefersPackageSpecInTheSameSchema() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        DbObject auditSpec = object(projectId, sourceProjectId, DbObjectType.PACKAGE, "AUDIT", "pkg", "create package AUDIT.pkg as end;");
        DbObject hrSpec = object(projectId, sourceProjectId, DbObjectType.PACKAGE, "HR", "pkg", "create package HR.pkg as end;");
        DbObject hrBody = object(projectId, sourceProjectId, DbObjectType.PACKAGE_BODY, "HR", "pkg", "create package body HR.pkg as end;");

        List<ObjectDependency> dependencies = service.rebuildProjectDependencies(projectId, List.of(auditSpec, hrSpec, hrBody));

        assertThat(dependencies).singleElement()
                .satisfies(dependency -> assertThat(dependency.targetObjectId()).isEqualTo(hrSpec.id()));
    }

    private DbObject object(UUID projectId, UUID sourceProjectId, DbObjectType type, String name, String sql) {
        return object(projectId, sourceProjectId, type, null, name, sql);
    }

    private DbObject object(UUID projectId, UUID sourceProjectId, DbObjectType type, String schema, String name, String sql) {
        return new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                UUID.randomUUID(),
                type,
                schema,
                name,
                ObjectStatus.PARSED,
                new SourceLocation(name + ".sql", 1, 1, 0, sql.length()),
                sql,
                List.of());
    }
}
