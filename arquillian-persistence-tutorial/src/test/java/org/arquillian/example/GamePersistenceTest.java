/*
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.arquillian.example;

import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.UserTransaction;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class GamePersistenceTest {
    @Deployment
    public static Archive<?> createDeployment() {
        // You can use war packaging...
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war").addPackage(Game.class.getPackage())
                .addAsResource("test-persistence.xml", "META-INF/persistence.xml").addAsWebInfResource("jbossas-ds.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");

        // or jar packaging...
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class).addPackage(Game.class.getPackage())
                .addAsManifestResource("test-persistence.xml", "persistence.xml")
                .addAsManifestResource("jbossas-ds.xml").addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

        // choose your packaging here
        return jar;
    }

    private static final String[] GAME_TITLES = { "Super Mario Brothers", "Mario Kart", "F-Zero" };

    @PersistenceContext
    EntityManager em;

    @Inject
    UserTransaction utx;

    @Before
    public void preparePersistenceTest() throws Exception {
        clearData();
        insertGameData();
        insertReviewData();
        startTransaction();
    }

    private void clearData() throws Exception {
        utx.begin();
        em.joinTransaction();
        System.out.println("Dumping old records...");
        em.createQuery("delete from GameReview").executeUpdate();
        em.createQuery("delete from Game").executeUpdate();
        utx.commit();
    }

    private void insertGameData() throws Exception {
        utx.begin();
        em.joinTransaction();
        System.out.println("Inserting games...");
        for (String title : GAME_TITLES) {
            Game game = new Game(title);
            em.persist(game);
        }
        utx.commit();
        // reset the persistence context (cache)
        em.clear();
    }

    private void insertReviewData() throws Exception {
        utx.begin();
        em.joinTransaction();
        System.out.println("Inserting reviews...");
        for (String title : GAME_TITLES) {
            Query query = em.createQuery("SELECT g FROM Game g WHERE g.title = :title");
            query.setParameter("title", title);
            Game game = (Game) query.getSingleResult();

            GameReview review = new GameReview();
            review.setGameId(game.getId());
            review.setScore(Math.random());
            /*
             * I need to set the game to have it later. This, in particular, i'm not setting
             * to get assert fail on shouldFindGameReviewWithGame() method
             */
            // review.setGame(game);
            em.persist(review);

            GameReview review2 = new GameReview();
            review2.setGame(game);
            review2.setGameId(game.getId());
            review2.setScore(Math.random());
            em.persist(review2);
        }
        utx.commit();
        // reset the persistence context (cache)
        em.clear();
    }

    private void startTransaction() throws Exception {
        utx.begin();
        em.joinTransaction();
    }

    @After
    public void commitTransaction() throws Exception {
        utx.commit();
    }

    @Test
    public void shouldFindGameReviewWithGame() {
        String sql = "SELECT gr FROM GameReview gr JOIN FETCH gr.game";
        List<GameReview> reviews = em.createQuery(sql, GameReview.class).getResultList();
        for (GameReview gr : reviews) {
            Assert.assertNotNull(gr.getGame());
        }
    }

    @Test
    public void shouldFindAllGamesAndReviewsJpqlQuery() {
        String sql = "SELECT g FROM Game g JOIN FETCH g.reviews ORDER BY g.id";
        List<Game> games = em.createQuery(sql, Game.class).getResultList();
        Assert.assertEquals(GAME_TITLES.length, games.size());
        for (Game game : games) {
            System.out.println("Game: " + game);
            System.out.println("Reviews on game: " + game.getReviews().size());
            Assert.assertTrue(game.getReviews().size() > 0);
            for (GameReview review : game.getReviews()) {
                System.out.println("Review: " + review);
            }
        }
    }

}
