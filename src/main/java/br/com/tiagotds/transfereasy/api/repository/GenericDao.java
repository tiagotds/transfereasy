package br.com.tiagotds.transfereasy.api.repository;

import java.io.Serializable;
import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import br.com.tiagotds.transfereasy.api.entity.BaseEntity;

@Stateless
public class GenericDao {

	private static EntityManager entityManager = JPAUtil.getEntityManagerFactory().createEntityManager();

	public enum MatchMode {
		START, END, EXACT, ANYWHERE
	}

	public <T extends BaseEntity<PK>, PK extends Serializable> PK save(T entity) {
		entityManager.persist(entity);
		return entity.getId();
	}

	public <T extends BaseEntity<PK>, PK extends Serializable> T merge(T entity) {
		return entityManager.merge(entity);
	}

	public <T extends BaseEntity<?>> List<T> findByProperty(Class<T> clazz, String propertyName, Object value) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> cq = cb.createQuery(clazz);
		Root<T> root = cq.from(clazz);
		cq.where(cb.equal(root.get(propertyName), value));
		return entityManager.createQuery(cq).getResultList();
	}

	public <T extends BaseEntity<?>> List<T> findAll(Class<T> clazz) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> cq = cb.createQuery(clazz);
		cq.from(clazz);
		return entityManager.createQuery(cq).getResultList();
	}

	public <T extends BaseEntity<?>> List<T> findByProperty(Class<T> clazz, String propertyName, String value,
			MatchMode matchMode) {
		// convert the value String to lowercase
		value = value.toLowerCase();
		if (MatchMode.START.equals(matchMode)) {
			value = value + "%";
		} else if (MatchMode.END.equals(matchMode)) {
			value = "%" + value;
		} else if (MatchMode.ANYWHERE.equals(matchMode)) {
			value = "%" + value + "%";
		}

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> cq = cb.createQuery(clazz);
		Root<T> root = cq.from(clazz);
		cq.where(cb.like(cb.lower(root.get(propertyName)), value));

		return entityManager.createQuery(cq).getResultList();
	}

	public synchronized void beginTransaction(boolean wasCalledInOtherProcess) throws InterruptedException {
		if (!wasCalledInOtherProcess) {
			entityManager.getTransaction().begin();
		}
	}

	public void commitTransaction(boolean wasCalledInOtherProcess) {
		if (!wasCalledInOtherProcess) {
			entityManager.getTransaction().commit();
		}
	}

	public void rollbackTransaction(boolean wasCalledInOtherProcess) {
		if (!wasCalledInOtherProcess) {
			entityManager.getTransaction().rollback();
		}
	}

	public void beginTransaction() throws InterruptedException {
		beginTransaction(false);
	}

	public void commitTransaction() {
		commitTransaction(false);
	}

	public void rollbackTransaction() {
		rollbackTransaction(false);
	}
}
