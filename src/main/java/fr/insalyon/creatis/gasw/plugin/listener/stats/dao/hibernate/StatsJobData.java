/* Copyright CNRS-CREATIS
 *
 * Rafael Ferreira da Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.rafaelsilva.com
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */
package fr.insalyon.creatis.gasw.plugin.listener.stats.dao.hibernate;

import fr.insalyon.creatis.gasw.GaswExitCode;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.plugin.listener.stats.dao.StatsJobDAO;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaBuilder;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Rafael Ferreira da Silva
 */
public class StatsJobData implements StatsJobDAO {

    private SessionFactory sessionFactory;

    public StatsJobData(SessionFactory sessionFactory) {

        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<Job> getByFilenameAndExitCode(String fileName, GaswExitCode exitCode) throws DAOException {

        try (Session session = sessionFactory.openSession()){
            session.beginTransaction();

            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<Job> criteriaQuery = criteriaBuilder.createQuery(Job.class);
            Root<Job> root = criteriaQuery.from(Job.class);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("fileName"), fileName));
            predicates.add(criteriaBuilder.equal(root.get("exitCode"), exitCode.getExitCode()));

            criteriaQuery.where(predicates.toArray(new Predicate[0]));

            List<Job> jobs = session.createQuery(criteriaQuery).list();

            session.getTransaction().commit();
            return jobs;

        } catch (HibernateException ex) {
            throw new DAOException(ex);
        }
    }
}
