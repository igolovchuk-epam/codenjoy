package com.codenjoy.dojo.web.rest;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2018 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.codenjoy.dojo.services.Dispatcher;
import com.codenjoy.dojo.services.dao.Players;
import com.codenjoy.dojo.services.entity.Player;
import com.codenjoy.dojo.services.entity.PlayerScore;
import com.codenjoy.dojo.services.entity.ServerLocation;
import com.codenjoy.dojo.web.controller.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Controller
@RequestMapping(value = "/rest")
public class RestController {

    @Autowired private Players players;
    @Autowired private Dispatcher dispatcher;
    @Autowired private Validator validator;

    private boolean adminPassword;

    @Value("${admin.password}")
    public void setActive(boolean adminPassword) {
        this.adminPassword = adminPassword;
    }

    @RequestMapping(value = "/score/day/{day}", method = RequestMethod.GET)
    @ResponseBody
    public List<PlayerScore> dayScores(@PathVariable("day") String day) {
        validator.checkDay(day);

        return dispatcher.getScores(day);
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public ServerLocation register(@RequestBody Player player, HttpServletRequest request) {
        String email = player.getEmail();
        validator.checkEmail(email, false);
        validator.checkString(player.getFirstName());
        validator.checkString(player.getLastName());
        validator.checkMD5(player.getPassword());
        validator.checkString(player.getCity());
        validator.checkString(player.getSkills());

        if (players.getCode(email) != null) {
            return unauthorized(email);
        }

        ServerLocation location = dispatcher.register(player, getIp(request));

        player.setCode(location.getCode());
        player.setServer(location.getServer());
        players.create(player);

        return location;
    }

    private String getIp(HttpServletRequest request) {
        String result = request.getRemoteAddr();
        if (result.equals("0:0:0:0:0:0:0:1")) {
            result = "127.0.0.1";
        }
        return result;
    }

    interface OnLogin<T> {
        T onSuccess(ServerLocation data);

        T onFailed(ServerLocation data);
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public ServerLocation login(@RequestBody Player player) {
        return tryLogin(player, new OnLogin<ServerLocation>(){

            @Override
            public ServerLocation onSuccess(ServerLocation data) {
                return data;
            }

            @Override
            public ServerLocation onFailed(ServerLocation data) {
                return data;
            }
        });
    }

    private <T> T tryLogin(Player player, OnLogin<T> onLogin) {
        String email = player.getEmail();
        String password = player.getPassword();

        validator.checkEmail(email, false);
        validator.checkMD5(password);

        Player exist = players.get(email);
        if (exist == null || !password.equals(exist.getPassword())) {
            return onLogin.onFailed(unauthorized(email));
        }
        String server = players.getServer(email);

        return onLogin.onSuccess(new ServerLocation(email, exist.getCode(), server));
    }

    @RequestMapping(value = "/remove", method = RequestMethod.POST)
    @ResponseBody
    public boolean remove(@RequestBody Player player) {
        return tryLogin(player, new OnLogin<Boolean>(){

            @Override
            public Boolean onSuccess(ServerLocation data) {
                players.remove(data.getEmail());
                dispatcher.remove(data.getServer(), data.getEmail(), data.getCode());
                return true;
            }

            @Override
            public Boolean onFailed(ServerLocation data) {
                return false;
            }
        });
    }

    private ServerLocation unauthorized(String email) {
        return new ServerLocation(email, null, null);
    }


    @RequestMapping(value = "/players", method = RequestMethod.GET)
    @ResponseBody
    public List<Player> getPlayers(@RequestBody Player player) {
        validator.validateAdmin(player, adminPassword);
        return players.getPlayersDetails();
    }
}